package de.civa.keymap;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A foldable, navigable view of a keymap's shortcut conflicts, produced live by
 * {@link ConflictScan}. Laid out in three parts, top to bottom:
 * <ol>
 *   <li><b>Keymap selector</b> — a dropdown of every installed keymap, defaulting to the active
 *       one; picking another re-scans and repopulates the whole report.</li>
 *   <li><b>Summary pane</b> — one prominent, icon-tagged status line for the selected keymap.</li>
 *   <li><b>Details pane</b> — a {@link Tree} navigating three sections (conflicts outside this
 *       keymap; the keymap's own conflicts, removable here; double-bound keys, informational) beside
 *       a detail view that explains the selected row and offers the fix links.</li>
 * </ol>
 */
public final class ConflictReportDialog extends DialogWrapper {
  /** Keymaps this plugin ships — grouped just below the active one in the selector. */
  private static final Set<String> OWN_KEYMAP_NAMES = Set.of("MacBook Pro DE");

  private final Project project;
  private Keymap keymap;   // the keymap the report currently previews (the combo selection)
  private Keymap active;   // the keymap actually active in the IDE (moves on Activate)
  private ConflictScan scan;
  private Tree tree;
  private JEditorPane detail;
  private JBLabel summaryText;
  private JBLabel summaryIcon;
  private ComboBox<Keymap> keymapCombo;
  private JPanel keymapCard;                                     // CardLayout: "combo" | "rename"
  private JBTextField renameField;                               // inline editor shown while renaming
  private final Set<Integer> separatorBefore = new HashSet<>();  // combo indices that start a group
  private boolean updatingModel;                                 // suppress the listener during rebuilds
  private JComponent buttonRow;

  public ConflictReportDialog(@Nullable Project project) {
    super(project);
    this.project = project;
    active = KeymapManager.getInstance().getActiveKeymap();
    keymap = active;
    scan = ConflictScan.of(keymap);
    setTitle("Review Keymap Conflicts");
    setResizable(true);
    init();
  }

  // Section titles (used both as node labels and to spot the low-priority, collapsed-by-default ones).
  private static final String SEC_OUTSIDE = "Conflicts outside this keymap";
  private static final String SEC_KEYMAP = "Keymap conflicts";
  private static final String SEC_IDEA_IGNORED = "Overlaps IntelliJ doesn't flag";
  private static final String SEC_DOUBLE = "Double-bound keys — informational, not conflicts";

  /** macOS shortcut ids IntelliJ's own keymap tool deliberately excludes from its conflict banner. */
  private static final Set<String> IDEA_IGNORED_IDS = Set.of("FocusNextApplicationWindow", "FocusPreviousApplicationWindow");

  /** Typed tree payloads; the renderer and detail pane switch on these. */
  private record Section(String title, int count) {}
  private record Empty(String message) {}
  private record KeymapItem(ConflictScan.ExternalConflict c) {}
  private record OutsideItem(ConflictScan.ExternalConflict c) {}
  private record IdeaIgnoredItem(ConflictScan.ExternalConflict c, boolean owned) {}

  @Override
  protected JComponent createCenterPanel() {
    tree = new Tree(new DefaultTreeModel(buildRoot()));
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new Renderer());
    tree.addTreeSelectionListener(e -> showDetail(payloadOf(e.getNewLeadSelectionPath())));

    detail = new JEditorPane("text/html", "");
    detail.setEditable(false);
    detail.setBorder(JBUI.Borders.empty(8, 10));
    detail.setBackground(UIUtil.getPanelBackground());
    detail.addHyperlinkListener(this::onLink);

    expandActionableSections();
    selectFirstConflict();

    // Details pane: the tree navigator and its per-row explanation.
    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.44f);
    splitter.setFirstComponent(new JBScrollPane(tree));
    splitter.setSecondComponent(new JBScrollPane(detail));

    // Header stacks the keymap selector above the prominent status summary.
    JPanel header = new JPanel(new BorderLayout());
    header.add(selectorPane(), BorderLayout.NORTH);
    header.add(summaryPane(), BorderLayout.CENTER);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(header, BorderLayout.NORTH);
    panel.add(splitter, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(JBUI.scale(820), JBUI.scale(600)));
    return panel;
  }

  // ---- keymap selector ------------------------------------------------------------------------

  /**
   * Title, combo box and action buttons stacked and centered. The combo lists the active keymap
   * first, then this plugin's keymaps, then everything else alphabetically, with a rule between
   * groups; each entry is tagged with its source. Activate/Reset appear only for a non-active pick.
   */
  private JComponent selectorPane() {
    keymapCombo = new ComboBox<>(orderedModel());
    keymapCombo.setSelectedItem(keymap);
    keymapCombo.setRenderer(new KeymapCellRenderer());
    keymapCombo.addActionListener(e -> {
      if (updatingModel) return;
      if (keymapCombo.getSelectedItem() instanceof Keymap k && !sameKeymap(k, keymap)) {
        keymap = k;
        refresh();
        updateActionButtons();
      }
    });

    // The combo and the inline rename editor share one slot, swapped by CardLayout.
    renameField = new JBTextField();
    renameField.registerKeyboardAction(e -> commitRename(),
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    renameField.registerKeyboardAction(e -> cancelRename(),
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    keymapCard = new JPanel(new CardLayout());
    keymapCard.add(keymapCombo, "combo");
    keymapCard.add(renameField, "rename");

    // Gear menu, like IDEA's scheme actions. The trailing triangle marks it as a menu button.
    Icon gearIcon = new RowIcon(AllIcons.General.GearPlain, AllIcons.General.ButtonDropTriangle);
    InplaceButton[] gear = new InplaceButton[1];
    gear[0] = new InplaceButton("Keymap actions", gearIcon, e -> showGearMenu(gear[0]));

    JButton activate = new JButton("Activate");
    activate.addActionListener(e -> activateSelected());
    JButton reset = new JButton("Reset");
    reset.addActionListener(e -> keymapCombo.setSelectedItem(active));
    buttonRow = centered(activate, reset);
    buttonRow.setVisible(false);

    JPanel selector = new JPanel();
    selector.setLayout(new BoxLayout(selector, BoxLayout.Y_AXIS));
    selector.setBorder(JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(8, 12)));
    selector.add(centered(new JBLabel("Keymap:"), keymapCard, gear[0]));
    selector.add(buttonRow);
    return selector;
  }

  /** A real IDEA action menu: the three exports and duplicate always, rename/delete when editable. */
  private void showGearMenu(Component anchor) {
    DefaultActionGroup group = new DefaultActionGroup(
      menuAction("Export full keymap (.xml)", false, () -> exportKeymap(ExportScope.FULL)),
      menuAction("Export only conflicting mappings (.xml)", false, () -> exportKeymap(ExportScope.CONFLICTS)),
      menuAction("Export conflicting + overlapping mappings (.xml)", false, () -> exportKeymap(ExportScope.CONFLICTS_AND_OVERLAPS)),
      Separator.getInstance(),
      menuAction("Duplicate", false, this::duplicateKeymap),
      menuAction("Rename…", true, this::startRename),
      menuAction("Delete…", true, this::deleteKeymap));
    ActionManager.getInstance().createActionPopupMenu("CivaKeymapGear", group)
      .getComponent().show(anchor, 0, anchor.getHeight());
  }

  /** A menu action; when {@code editableOnly}, it is hidden unless the selected keymap can be modified. */
  private AnAction menuAction(String text, boolean editableOnly, Runnable run) {
    return new AnAction(text) {
      @Override public void actionPerformed(@NotNull AnActionEvent e) { run.run(); }
      @Override public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(!editableOnly || keymap.canModify());
      }
      @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    };
  }

  // ---- duplicate / rename / delete ------------------------------------------------------------

  /** Make an editable copy of the selected keymap and preview it (use Activate to switch to it). */
  private void duplicateKeymap() {
    Keymap copy = keymap.deriveKeymap(uniqueKeymapName(keymap.getPresentableName()));
    KeymapManagerEx.getInstanceEx().getSchemeManager().addScheme(copy);
    keymap = copy;            // select and preview the copy; the active keymap is left unchanged
    reloadKeymaps();
    refresh();
    updateActionButtons();    // Activate/Reset appear, since the copy differs from the active keymap
  }

  /** Swap the combo for an inline text editor (Enter saves, Esc cancels), like IDEA's scheme rename. */
  private void startRename() {
    if (!keymap.canModify()) return;
    renameField.setText(keymap.getPresentableName());
    ((CardLayout) keymapCard.getLayout()).show(keymapCard, "rename");
    renameField.selectAll();
    renameField.requestFocusInWindow();
  }

  private void cancelRename() {
    ((CardLayout) keymapCard.getLayout()).show(keymapCard, "combo");
  }

  private void commitRename() {
    String newName = renameField.getText().trim();
    String current = keymap.getName();
    if (newName.isEmpty() || newName.equals(current)) { cancelRename(); return; }
    for (Keymap k : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (!sameKeymap(k, keymap) && newName.equals(k.getName())) {
        Messages.showErrorDialog(project, "A keymap named “" + newName + "” already exists.", "Rename Keymap");
        renameField.requestFocusInWindow();
        return;
      }
    }
    if (!(keymap instanceof ExternalizableScheme scheme)) { cancelRename(); return; }
    // Re-register under the new name so the scheme manager's name index stays consistent.
    boolean wasActive = sameKeymap(keymap, active);
    KeymapManagerEx manager = KeymapManagerEx.getInstanceEx();
    manager.getSchemeManager().removeScheme(keymap);
    scheme.setName(newName);
    manager.getSchemeManager().addScheme(keymap);
    if (wasActive) { manager.setActiveKeymap(keymap); active = keymap; }
    cancelRename();
    reloadKeymaps();
  }

  private void deleteKeymap() {
    if (!keymap.canModify()) return;
    int choice = Messages.showYesNoDialog(project,
      "Delete the keymap “" + keymap.getPresentableName() + "”? This cannot be undone.",
      "Delete Keymap", "Delete", "Cancel", Messages.getWarningIcon());
    if (choice != Messages.YES) return;

    KeymapManagerEx manager = KeymapManagerEx.getInstanceEx();
    Keymap deleted = keymap;
    boolean wasActive = sameKeymap(deleted, active);
    manager.getSchemeManager().removeScheme(deleted);
    if (wasActive) {
      Keymap fallback = deleted.getParent();
      if (fallback == null) {
        Keymap[] remaining = manager.getAllKeymaps();
        fallback = remaining.length > 0 ? remaining[0] : null;
      }
      if (fallback == null) return;  // nothing left to fall back to (should never happen)
      manager.setActiveKeymap(fallback);
      active = fallback;
      keymap = fallback;
    }
    else {
      keymap = active;  // a previewed, non-active keymap was deleted — keep the active one
    }
    reloadKeymaps();
    refresh();
    updateActionButtons();
  }

  /** A row that lays out its children centered at their preferred size. */
  private static JComponent centered(Component... children) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, JBUI.scale(6), JBUI.scale(2)));
    for (Component c : children) row.add(c);
    return row;
  }

  /** Combo contents in group order — active, then own, then the rest — recording group starts. */
  private DefaultComboBoxModel<Keymap> orderedModel() {
    List<Keymap> own = new ArrayList<>();
    List<Keymap> rest = new ArrayList<>();
    Keymap activeEntry = null;
    for (Keymap k : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (sameKeymap(k, active)) activeEntry = k;
      else if (isOwn(k)) own.add(k);
      else rest.add(k);
    }
    Comparator<Keymap> byName = Comparator.comparing(Keymap::getPresentableName, String.CASE_INSENSITIVE_ORDER);
    own.sort(byName);
    rest.sort(byName);

    separatorBefore.clear();
    List<Keymap> ordered = new ArrayList<>();
    if (activeEntry != null) ordered.add(activeEntry);
    if (!own.isEmpty()) { if (!ordered.isEmpty()) separatorBefore.add(ordered.size()); ordered.addAll(own); }
    if (!rest.isEmpty()) { if (!ordered.isEmpty()) separatorBefore.add(ordered.size()); ordered.addAll(rest); }
    return new DefaultComboBoxModel<>(ordered.toArray(new Keymap[0]));
  }

  /** Where a keymap comes from — the plugin only reliably knows the active one and its own. */
  private String sourceTag(Keymap k) {
    if (sameKeymap(k, active)) return "(active)";
    if (isOwn(k)) return "(this plugin)";
    return k.canModify() ? "(custom)" : "(built-in)";
  }

  private void activateSelected() {
    KeymapManagerEx.getInstanceEx().setActiveKeymap(keymap);
    active = keymap;
    reloadKeymaps();  // new active moves to the top, tags refresh
    updateActionButtons();
  }

  /** Rebuild the combo from the current keymap list without firing the selection listener. */
  private void reloadKeymaps() {
    updatingModel = true;
    try {
      keymapCombo.setModel(orderedModel());
      keymapCombo.setSelectedItem(keymap);
    }
    finally {
      updatingModel = false;
    }
  }

  private void updateActionButtons() {
    buttonRow.setVisible(!sameKeymap(keymap, active));
    buttonRow.revalidate();
    buttonRow.repaint();
  }

  private static boolean isOwn(Keymap k) {
    return OWN_KEYMAP_NAMES.contains(k.getName());
  }

  private static boolean sameKeymap(@Nullable Keymap a, @Nullable Keymap b) {
    return a != null && b != null && a.getName().equals(b.getName());
  }

  // ---- bulk rebind ----------------------------------------------------------------------------

  /**
   * Move every mapping on one keystroke to a new one at once — the bulk change IDE Settings can't do
   * (there you would re-key each action individually). Read-only keymaps are copied to an editable
   * one first (and activated), since {@link Keymap#addShortcut}/{@link Keymap#removeShortcut} only
   * take on a modifiable keymap.
   */
  private void rebindConflict(ConflictScan.ExternalConflict c) {
    KeyStroke oldFirst = c.stroke();
    String notice = keymap.canModify() ? null
      : "“" + keymap.getPresentableName() + "” is read-only — an editable copy will be created and activated.";
    ShortcutInputDialog dialog = new ShortcutInputDialog(project, keymap, oldFirst, notice);
    if (!dialog.showAndGet()) return;
    KeyStroke newFirst = dialog.getResult();
    if (newFirst == null || newFirst.equals(oldFirst)) return;

    Keymap target = ensureEditable();
    if (target == null) return;

    // Collect first, mutate second: removeShortcut/addShortcut change what getShortcuts returns.
    record Move(String id, KeyboardShortcut from, KeyboardShortcut to) {}
    List<Move> moves = new ArrayList<>();
    for (String id : target.getActionIdList()) {
      for (Shortcut sc : target.getShortcuts(id)) {
        if (sc instanceof KeyboardShortcut ks && oldFirst.equals(ks.getFirstKeyStroke())) {
          moves.add(new Move(id, ks, new KeyboardShortcut(newFirst, ks.getSecondKeyStroke())));
        }
      }
    }
    for (Move m : moves) {
      target.removeShortcut(m.id(), m.from());
      target.addShortcut(m.id(), m.to());
    }
    refresh();
    updateActionButtons();
  }

  /** The selected keymap if modifiable, else a freshly derived, registered and activated copy. */
  private @Nullable Keymap ensureEditable() {
    if (keymap.canModify()) return keymap;
    Keymap copy = keymap.deriveKeymap(uniqueKeymapName(keymap.getPresentableName()));
    KeymapManagerEx manager = KeymapManagerEx.getInstanceEx();
    manager.getSchemeManager().addScheme(copy);
    manager.setActiveKeymap(copy);
    keymap = copy;
    active = copy;
    reloadKeymaps();
    return copy;
  }

  private static String uniqueKeymapName(String base) {
    Set<String> taken = new HashSet<>();
    for (Keymap k : KeymapManagerEx.getInstanceEx().getAllKeymaps()) taken.add(k.getName());
    String candidate = base + " (editable)";
    for (int n = 2; taken.contains(candidate); n++) candidate = base + " (editable " + n + ")";
    return candidate;
  }

  private static int countBoundTo(Keymap keymap, KeyStroke firstStroke) {
    int count = 0;
    for (String id : keymap.getActionIdList()) {
      for (Shortcut sc : keymap.getShortcuts(id)) {
        if (sc instanceof KeyboardShortcut ks && firstStroke.equals(ks.getFirstKeyStroke())) count++;
      }
    }
    return count;
  }

  /** Two-column combo cell: keymap name flush left, its source tag flush right. */
  private final class KeymapCellRenderer implements ListCellRenderer<Keymap> {
    private final JLabel name = new JLabel();
    private final JLabel source = new JLabel();
    private final JPanel panel = new JPanel(new BorderLayout(JBUI.scale(16), 0));

    KeymapCellRenderer() {
      panel.setOpaque(true);
      source.setBorder(JBUI.Borders.emptyRight(6));  // a space of breathing room at the right edge
      panel.add(name, BorderLayout.WEST);
      panel.add(source, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Keymap> list, Keymap value,
                                                  int index, boolean selected, boolean hasFocus) {
      name.setText(value == null ? "" : value.getPresentableName());
      source.setText(value == null ? "" : sourceTag(value));
      Color fg = selected ? list.getSelectionForeground() : list.getForeground();
      panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
      name.setForeground(fg);
      source.setForeground(selected ? fg : grayColour());
      Border pad = JBUI.Borders.empty(1, 8);
      panel.setBorder(separatorBefore.contains(index)
        ? JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()), pad)
        : pad);
      return panel;
    }
  }

  // ---- tree model -----------------------------------------------------------------------------

  private DefaultMutableTreeNode buildRoot() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    // Overlaps IntelliJ's keymap tool deliberately ignores (window switching) go in their own
    // section rather than mixed into the real conflicts; the rest keep their normal grouping.
    List<ConflictScan.ExternalConflict> outsideNormal = scan.outsideConflicts.stream().filter(c -> !ideaIgnored(c)).toList();
    List<ConflictScan.ExternalConflict> keymapNormal = scan.keymapConflicts.stream().filter(c -> !ideaIgnored(c)).toList();

    if (!outsideNormal.isEmpty()) {
      DefaultMutableTreeNode outside = new DefaultMutableTreeNode(new Section(SEC_OUTSIDE, outsideNormal.size()));
      for (ConflictScan.ExternalConflict c : outsideNormal) outside.add(new DefaultMutableTreeNode(new OutsideItem(c)));
      root.add(outside);
    }

    // The curated SUPPLEMENT entries describe our keymap's specific overlaps, so show them only for it.
    List<ConflictAdvice.Supplement> supplements = scan.ownKeymap ? ConflictAdvice.SUPPLEMENT : List.of();
    DefaultMutableTreeNode keymapNode = new DefaultMutableTreeNode(
      new Section(SEC_KEYMAP, keymapNormal.size() + supplements.size()));
    if (!scan.jbrApiAvailable) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("macOS scan unavailable on this runtime.")));
    }
    else if (keymapNormal.isEmpty() && supplements.isEmpty()) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("No conflicts with macOS. Nothing to change.")));
    }
    for (ConflictScan.ExternalConflict c : keymapNormal) keymapNode.add(new DefaultMutableTreeNode(new KeymapItem(c)));
    for (ConflictAdvice.Supplement s : supplements) keymapNode.add(new DefaultMutableTreeNode(s));
    root.add(keymapNode);

    // IntelliJ-ignored overlaps, keeping ownership so the detail pane can still offer a cosmetic fix.
    List<IdeaIgnoredItem> ignored = new ArrayList<>();
    for (ConflictScan.ExternalConflict c : scan.keymapConflicts) if (ideaIgnored(c)) ignored.add(new IdeaIgnoredItem(c, true));
    for (ConflictScan.ExternalConflict c : scan.outsideConflicts) if (ideaIgnored(c)) ignored.add(new IdeaIgnoredItem(c, false));
    if (!ignored.isEmpty()) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Section(SEC_IDEA_IGNORED, ignored.size()));
      for (IdeaIgnoredItem item : ignored) node.add(new DefaultMutableTreeNode(item));
      root.add(node);
    }

    DefaultMutableTreeNode dbl = new DefaultMutableTreeNode(new Section(SEC_DOUBLE, scan.internal.size()));
    for (ConflictScan.InternalConflict c : scan.internal) dbl.add(new DefaultMutableTreeNode(c));
    root.add(dbl);

    return root;
  }

  /** True when macOS claims the key only via shortcuts IntelliJ's keymap tool ignores (window switching). */
  private static boolean ideaIgnored(ConflictScan.ExternalConflict c) {
    if (c.macOs().isEmpty()) return false;
    for (ConflictScan.SystemShortcut s : c.macOs()) {
      if (s.id() == null || !IDEA_IGNORED_IDS.contains(s.id())) return false;
    }
    return true;
  }

  private void refresh() {
    scan = ConflictScan.of(keymap);
    tree.setModel(new DefaultTreeModel(buildRoot()));
    expandActionableSections();
    selectFirstConflict();
    updateSummary();
  }

  /** Expand the actionable sections; leave the informational ones (double-bound, IntelliJ-ignored) collapsed. */
  private void expandActionableSections() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      if (isInformationalSection(section.getUserObject())) continue;
      tree.expandPath(new TreePath(section.getPath()));
    }
  }

  private void selectFirstConflict() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      if (isInformationalSection(section.getUserObject())) continue;
      if (section.getChildCount() > 0) {
        DefaultMutableTreeNode first = (DefaultMutableTreeNode) section.getFirstChild();
        tree.setSelectionPath(new TreePath(first.getPath()));
        return;
      }
    }
  }

  private static boolean isInformationalSection(Object payload) {
    return payload instanceof Section s && (s.title().equals(SEC_DOUBLE) || s.title().equals(SEC_IDEA_IGNORED));
  }

  private @Nullable Object payloadOf(@Nullable TreePath path) {
    return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
  }

  // ---- links ----------------------------------------------------------------------------------

  private void onLink(HyperlinkEvent e) {
    if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
    String href = e.getDescription();
    if ("civa:settings".equals(href)) {
      openKeymapSettings();
      return;
    }
    ConflictScan.ExternalConflict conflict = selectedConflict();
    if (conflict == null) return;
    if ("civa:rebind".equals(href)) {
      rebindConflict(conflict);
    }
    else if ("civa:remove".equals(href)) {
      removeConflict(conflict);
    }
  }

  /** The external conflict backing the selected row, whether it is the keymap's own or from outside. */
  private ConflictScan.@Nullable ExternalConflict selectedConflict() {
    Object payload = payloadOf(tree.getSelectionPath());
    if (payload instanceof KeymapItem ki) return ki.c();
    if (payload instanceof OutsideItem oi) return oi.c();
    if (payload instanceof IdeaIgnoredItem ii) return ii.c();
    return null;
  }

  /** Clear every matching binding on the conflict's keystroke, copying a read-only keymap first. */
  private void removeConflict(ConflictScan.ExternalConflict c) {
    Keymap target = ensureEditable();
    if (target == null) return;
    KeyStroke first = c.stroke();
    for (ConflictScan.ActionRef a : c.actions()) {
      for (Shortcut sc : target.getShortcuts(a.id())) {
        if (sc instanceof KeyboardShortcut ks && first.equals(ks.getFirstKeyStroke())) {
          target.removeShortcut(a.id(), ks);
        }
      }
    }
    refresh();
    updateActionButtons();
  }

  private void openKeymapSettings() {
    Project p = project;
    close(OK_EXIT_CODE);
    ApplicationManager.getApplication().invokeLater(
      () -> ShowSettingsUtil.getInstance().showSettingsDialog(p, KeymapPanel.class));
  }

  // ---- summary --------------------------------------------------------------------------------

  /** Prominent, always-visible status line; kept in sync by {@link #updateSummary()}. */
  private JComponent summaryPane() {
    summaryIcon = new JBLabel();
    summaryText = new JBLabel();
    summaryText.setFont(summaryText.getFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(13f)));
    updateSummary();

    JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    row.setBorder(JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBColor.border()),
      JBUI.Borders.empty(10, 12)));
    row.add(summaryIcon, BorderLayout.WEST);
    row.add(summaryText, BorderLayout.CENTER);
    return row;
  }

  private void updateSummary() {
    int total = scan.keymapConflicts.size() + scan.outsideConflicts.size();
    long broken = scan.keymapConflicts.stream().filter(c -> needsAttention(c)).count()
                + scan.outsideConflicts.stream().filter(c -> needsAttention(c)).count();
    String text;
    Icon icon;
    if (!scan.jbrApiAvailable) {
      text = "The macOS shortcut check is unavailable on this runtime.";
      icon = AllIcons.General.Information;
    }
    else if (total == 0) {
      text = "<b>No overlaps with macOS system shortcuts.</b>";
      icon = AllIcons.General.InspectionsOK;
    }
    else if (broken == 0) {
      text = "<b>Nothing broken.</b> " + total + " shortcut(s) overlap macOS but keep working.";
      icon = AllIcons.General.Information;
    }
    else {
      text = "<b>" + broken + " shortcut(s) need attention</b> — macOS takes the key first. "
        + (total - broken) + " more overlap but keep working.";
      icon = AllIcons.General.Warning;
    }
    summaryText.setText("<html>" + text + "</html>");
    summaryIcon.setIcon(icon);
  }

  private static boolean needsAttention(ConflictScan.ExternalConflict c) {
    return c.advice().category() != ConflictAdvice.Category.DELIBERATE;
  }

  // ---- export ---------------------------------------------------------------------------------

  /** What subset of the selected keymap an export writes, all in the internal keymap XML format. */
  private enum ExportScope {
    FULL("-full", "the full keymap"),
    CONFLICTS("-conflicts", "the conflicting mappings"),
    CONFLICTS_AND_OVERLAPS("-conflicts-overlaps", "the conflicting and overlapping mappings");

    final String suffix;
    final String noun;
    ExportScope(String suffix, String noun) { this.suffix = suffix; this.noun = noun; }
  }

  /**
   * Serialize the selected keymap to a user-chosen {@code .xml} file in the platform's own keymap
   * format ({@link KeymapImpl#writeScheme()}). For the two conflict scopes the serialized element is
   * pruned to the affected {@code <action>} entries; only bindings the keymap itself declares can be
   * exported this way, so conflicts carried by inherited or other-plugin bindings are not included.
   */
  private void exportKeymap(ExportScope scope) {
    if (!(keymap instanceof KeymapImpl impl)) {
      Messages.showErrorDialog(project, "This keymap cannot be serialized to XML on this runtime.", "Export Failed");
      return;
    }
    Element root = impl.writeScheme();  // <keymap ...> with <action> children
    if (scope != ExportScope.FULL) {
      Set<String> keep = conflictingActionIds(scope == ExportScope.CONFLICTS_AND_OVERLAPS);
      for (Element action : new ArrayList<>(root.getChildren("action"))) {
        if (!keep.contains(action.getAttributeValue("id"))) root.removeContent(action);
      }
      if (root.getChildren("action").isEmpty()) {
        Messages.showInfoMessage(project, "This keymap declares no " + scope.noun + " to export.", "Nothing to Export");
        return;
      }
    }

    FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Keymap", "Save the keymap as XML", "xml");
    FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
    VirtualFileWrapper wrapper = dialog.save("keymap-" + sanitize(keymap.getName()) + scope.suffix + ".xml");
    if (wrapper == null) return;  // user cancelled
    File file = wrapper.getFile();
    try {
      Files.writeString(file.toPath(), JDOMUtil.writeElement(root), StandardCharsets.UTF_8);
    }
    catch (IOException ex) {
      Messages.showErrorDialog(project, "Could not write the keymap:\n" + ex.getMessage(), "Export Failed");
      return;
    }
    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (project != null && vf != null) {
      FileEditorManager.getInstance(project).openFile(vf, true);
    }
    else {
      Messages.showInfoMessage(project, "Keymap saved to:\n" + file.getAbsolutePath(), "Export Complete");
    }
  }

  /** Action ids caught by the macOS scan — the ones needing attention, plus benign overlaps when asked. */
  private Set<String> conflictingActionIds(boolean includeOverlaps) {
    Set<String> ids = new HashSet<>();
    List<ConflictScan.ExternalConflict> all = new ArrayList<>(scan.keymapConflicts);
    all.addAll(scan.outsideConflicts);
    for (ConflictScan.ExternalConflict c : all) {
      if (!includeOverlaps && !needsAttention(c)) continue;
      for (ConflictScan.ActionRef a : c.actions()) ids.add(a.id());
    }
    return ids;
  }

  private static String sanitize(String s) {
    String cleaned = s.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return cleaned.isEmpty() ? "keymap" : cleaned;
  }

  // ---- detail pane ----------------------------------------------------------------------------

  private void showDetail(@Nullable Object payload) {
    if (payload == null) { detail.setText(""); return; }
    StringBuilder sb = new StringBuilder("<html><body style='margin:0'>");
    if (payload instanceof Section s) {
      sb.append("<h3 style='margin:0 0 4px 0'>").append(escape(s.title())).append("</h3>")
        .append(grayBlock(escape(sectionBlurb(s.title()))));
    }
    else if (payload instanceof Empty e) {
      sb.append(grayBlock(escape(e.message())));
    }
    else if (payload instanceof KeymapItem ki) {
      appendConflict(sb, ki.c(), true, null);
    }
    else if (payload instanceof OutsideItem oi) {
      appendConflict(sb, oi.c(), false, null);
    }
    else if (payload instanceof IdeaIgnoredItem ii) {
      appendConflict(sb, ii.c(), ii.owned(),
        "IntelliJ's own Keymap settings does <b>not</b> flag this as a conflict — it deliberately "
        + "ignores window-switching overlaps — so no conflict marker appears for it there. It is "
        + "listed here only for completeness; the shortcut works. Changing it is an optional, purely "
        + "cosmetic tidy-up.");
    }
    else if (payload instanceof ConflictAdvice.Supplement s) {
      sb.append(keyHead(s.keys()))
        .append(factsTable(new String[][]{
          {"IntelliJ", escape(s.ideaSide())},
          {"macOS", escape(s.macSide())}}))
        .append(callout("What this means", escape(s.note())));
    }
    else if (payload instanceof ConflictScan.InternalConflict c) {
      sb.append(keyHead(KeymapUtil.getShortcutText(c.shortcut())))
        .append(grayBlock(c.actions().size() + " actions use this shortcut. This is not a conflict — "
          + "IntelliJ picks the right one based on where you are (editor, a tool window, a dialog)."))
        .append(factsTable(new String[][]{{"Actions", actionList(c.actions())}}));
    }
    sb.append("</body></html>");
    detail.setText(sb.toString());
    detail.setCaretPosition(0);
  }

  private void appendConflict(StringBuilder sb, ConflictScan.ExternalConflict c, boolean owned,
                              @Nullable String extraNoteHtml) {
    sb.append(keyHead(KeymapUtil.getKeystrokeText(c.stroke())))
      .append(statusBadge(c));
    String[][] rows = owned
      ? new String[][]{{"IntelliJ action", actionList(c.actions())}, {"macOS", escape(macList(c))}}
      : new String[][]{{"Used by", actionList(c.actions())},
                       {"Source", escape(sourceText(c.actions()))},
                       {"macOS", escape(macList(c))}};
    sb.append(factsTable(rows));
    if (!owned) {
      sb.append(grayBlock("This shortcut comes from another plugin or from IntelliJ itself, not from "
        + "this keymap. You can still override it here — that edits your editable keymap (creating one "
        + "if needed), the same as changing it in IntelliJ's Keymap settings."));
    }
    sb.append(callout("What to do", escape(c.advice().note())));
    if (extraNoteHtml != null) {
      sb.append(callout("Not flagged by IntelliJ", extraNoteHtml));  // already HTML; not escaped
    }
    sb.append(links());
  }

  private static String links() {
    String sep = " &nbsp;&nbsp;•&nbsp;&nbsp; ";
    return "<div style='margin:12px 0 0 0'>"
         + "<a href='civa:rebind'>Rebind all to…</a>" + sep
         + "<a href='civa:remove'>Remove this shortcut</a>" + sep
         + "<a href='civa:settings'>Open Keymap settings</a></div>";
  }

  /** Distinct providing plugins for a set of actions, joined; "Unknown" when none could be resolved. */
  private static String sourceText(List<ConflictScan.ActionRef> actions) {
    List<String> sources = actions.stream().map(ConflictScan.ActionRef::source)
      .filter(s -> s != null && !s.isEmpty()).distinct().toList();
    return sources.isEmpty() ? "Unknown" : String.join(", ", sources);
  }

  // ---- detail formatting helpers --------------------------------------------------------------

  private static String keyHead(String key) {
    return "<div style='font-size:15pt; font-weight:bold; margin:0 0 4px 0'>" + escape(key) + "</div>";
  }

  private String statusBadge(ConflictScan.ExternalConflict c) {
    boolean ok = c.advice().category() == ConflictAdvice.Category.DELIBERATE;
    String colour = hex(ok ? okColour() : warnColour());
    String glyph = ok ? "&#10004;" : "&#9888;";  // ✔ / ⚠
    return "<div style='margin:0 0 8px 0; font-weight:bold; color:" + colour + "'>"
      + glyph + "&nbsp; " + escape(status(c)) + "</div>";
  }

  private String factsTable(String[][] rows) {
    StringBuilder t = new StringBuilder("<table cellpadding='2' cellspacing='0' style='margin:2px 0'>");
    for (String[] r : rows) {
      t.append("<tr><td valign='top'><span style='color:").append(hex(grayColour()))
        .append("'><b>").append(escape(r[0])).append("</b></span>&nbsp;&nbsp;&nbsp;</td>")
        .append("<td valign='top'>").append(r[1]).append("</td></tr>");
    }
    return t.append("</table>").toString();
  }

  private String callout(String title, String bodyHtml) {
    return "<div style='margin:12px 0 3px 0; font-weight:bold'>" + escape(title) + "</div>"
      + "<div style='margin:0'>" + bodyHtml + "</div>";
  }

  private String grayBlock(String bodyHtml) {
    return "<div style='margin:2px 0 6px 0; color:" + hex(grayColour()) + "'>" + bodyHtml + "</div>";
  }

  private static String hex(Color c) {
    return String.format("#%06x", c.getRGB() & 0xFFFFFF);
  }

  private Color grayColour() {
    return UIUtil.getContextHelpForeground();
  }

  private static Color warnColour() {
    return new JBColor(new Color(0xB3261E), new Color(0xF2B8B5));
  }

  private static Color okColour() {
    return new JBColor(new Color(0x367C39), new Color(0x9CCC9C));
  }

  private static String status(ConflictScan.ExternalConflict c) {
    return switch (c.advice().category()) {
      case RESOLVE -> "macOS takes this key first, so the IntelliJ shortcut does not work here.";
      case UNCLASSIFIED -> "macOS may take this key, so the IntelliJ shortcut might not work here.";
      case DELIBERATE -> "This overlaps a macOS shortcut, but the IntelliJ shortcut keeps working.";
    };
  }

  private static String sectionBlurb(String title) {
    if (title.startsWith("Conflicts outside")) {
      return "Shortcuts from other plugins or from IntelliJ that sit on a key macOS also uses. "
        + "They aren't part of this keymap, so they can't be changed here — but this explains why "
        + "a shortcut may not be working, and where to change it.";
    }
    if (title.startsWith("Keymap conflicts")) {
      return "This keymap's own shortcuts that overlap a macOS system shortcut. Select one to see "
        + "whether it still works and to remove or change it.";
    }
    if (title.equals(SEC_IDEA_IGNORED)) {
      return "These shortcuts sit on a key macOS also uses for switching windows, but IntelliJ gets "
        + "it first while its window is in front, so they keep working. IntelliJ's own Keymap settings "
        + "deliberately ignores these overlaps and shows no conflict marker for them — they are listed "
        + "here only for completeness. Selecting one still lets you change it, if you prefer.";
    }
    return "One shortcut bound to several actions inside the keymap. Not a conflict — IntelliJ "
      + "picks the action that fits where you are. Shown for reference only.";
  }

  private static String macList(ConflictScan.ExternalConflict c) {
    return String.join("; ", c.macOs().stream().map(ConflictScan.SystemShortcut::label).distinct().toList());
  }

  private static String actionList(List<ConflictScan.ActionRef> actions) {
    StringBuilder sb = new StringBuilder();
    for (ConflictScan.ActionRef a : actions) {
      if (sb.length() > 0) sb.append("<br>");
      sb.append("<b>").append(escape(a.label())).append("</b>");
      if (a.displayName() != null && !a.displayName().isEmpty()) {
        sb.append(" <span style='color:gray'>").append(escape(a.id())).append("</span>");
      }
    }
    return sb.toString();
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Override
  protected com.intellij.openapi.ui.DialogWrapper.@Nullable DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  // ---- renderer -------------------------------------------------------------------------------

  private static final class Renderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
      Object p = ((DefaultMutableTreeNode) value).getUserObject();
      if (p instanceof Section s) {
        append(s.title(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append("  (" + s.count() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (p instanceof Empty e) {
        setIcon(AllIcons.General.InspectionsOK);
        append(e.message(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (p instanceof KeymapItem ki) {
        renderConflict(ki.c(), true);
      }
      else if (p instanceof OutsideItem oi) {
        renderConflict(oi.c(), false);
      }
      else if (p instanceof IdeaIgnoredItem ii) {
        renderConflict(ii.c(), ii.owned());
      }
      else if (p instanceof ConflictAdvice.Supplement s) {
        setIcon(AllIcons.General.Information);
        append(s.keys(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append("  " + s.macSide(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (p instanceof ConflictScan.InternalConflict c) {
        append(KeymapUtil.getShortcutText(c.shortcut()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append("  " + c.actions().size() + " actions", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }

    private void renderConflict(ConflictScan.ExternalConflict c, boolean owned) {
      setIcon(needsAttention(c) ? AllIcons.General.Warning : AllIcons.General.Information);
      append(KeymapUtil.getKeystrokeText(c.stroke()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      List<ConflictScan.ActionRef> a = c.actions();
      String suffix = a.isEmpty() ? "" : a.get(0).label() + (a.size() > 1 ? " +" + (a.size() - 1) : "");
      append("  " + suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      // For bindings not from this keymap, show where they come from. JTree cells are sized to their
      // content (no free space to the right), so the tag is appended inline rather than right-aligned;
      // the detail pane shows it as its own row.
      String source = owned ? null : sourceText(a);
      if (source != null) append("   (" + source + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  // ---- shortcut input dialog ------------------------------------------------------------------

  /**
   * Enters a keyboard shortcut two ways at once: the key field <b>grabs a full keypress</b> — press
   * ⌘Ü and the modifier checkboxes and the single-char key fill in — while a bare keypress or manual
   * checkbox toggling still works if capture can't read the key (dead keys, odd layouts). Capture runs
   * through an {@link IdeEventQueue} dispatcher scoped to the dialog, so it sees keys even when they
   * are bound to an IDE action (⌃X would otherwise be swallowed as Cut). The key maps to a {@link
   * KeyStroke} via {@link KeyEvent#getExtendedKeyCodeForChar}, which also covers the German keys
   * (Ä Ö Ü ß). A live status line flags whether the assembled shortcut hits macOS <i>or</i> is
   * already used elsewhere in this keymap.
   */
  private static final class ShortcutInputDialog extends DialogWrapper {
    private static final int MODIFIER_MASK = InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                                           | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    // Keys that can't be typed/pressed into the field (bare ones drive the dialog); pick via a radio.
    private static final int[] SPECIAL_CODES = {KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_TAB};
    private static final String[] SPECIAL_SYMBOLS = {"↩", "⎋", "⌫", "⇥"};

    private final Keymap keymap;
    private final KeyStroke original;
    private final int affected;
    private final @Nullable String notice;
    private final @Nullable Map<KeyStroke, List<ConflictScan.SystemShortcut>> system = ConflictScan.systemShortcuts();
    private final JCheckBox control = new JCheckBox("⌃ Control");
    private final JCheckBox option = new JCheckBox("⌥ Option");
    private final JCheckBox shift = new JCheckBox("⇧ Shift");
    private final JCheckBox command = new JCheckBox("⌘ Command");
    private final JBTextField keyField = new JBTextField(6);
    private final JBLabel preview = new JBLabel();
    private final JBLabel status = new JBLabel();
    private final ButtonGroup specialGroup = new ButtonGroup();
    private JRadioButton[] specialRadios;
    private int keyCode = KeyEvent.VK_UNDEFINED;  // the chosen key; the source of truth for build()
    private boolean settingText;                  // true while the field is set programmatically
    private KeyStroke result;

    ShortcutInputDialog(@Nullable Project project, Keymap keymap, KeyStroke original, @Nullable String notice) {
      super(project);
      this.keymap = keymap;
      this.original = original;
      this.affected = countBoundTo(keymap, original);
      this.notice = notice;
      setTitle("Rebind Shortcut");
      init();
      prefill();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return keyField;  // focus the capture field so pressing a shortcut works immediately
    }

    @Override
    protected JComponent createCenterPanel() {
      keyField.setToolTipText("Press the new shortcut (e.g. ⌃↩ or ⌘Ü), or type a single key.");
      limitToOneChar(keyField);
      // A plain KeyListener never sees IDE-bound combos (⌃X = Cut) — the keymap dispatcher eats them
      // first. Intercepting modifier combos at the event queue, ahead of that dispatcher, lets the
      // field grab them (incl. ⌃Enter); bare keys fall through so typing and dialog navigation work.
      IdeEventQueue.getInstance().addDispatcher(new IdeEventQueue.NonLockedEventDispatcher() {
        @Override public boolean dispatch(@NotNull AWTEvent event) {
          if (!(event instanceof KeyEvent ke) || !keyField.isFocusOwner()) return false;
          if ((ke.getModifiersEx() & MODIFIER_MASK) == 0) return false;  // bare key → type / navigate
          if (ke.getID() == KeyEvent.KEY_PRESSED) capture(ke);
          return true;  // swallow the combo so the IDE does not run it (e.g. ⌃X = Cut)
        }
      }, getDisposable());
      keyField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override protected void textChanged(@NotNull DocumentEvent e) {
          if (settingText) return;  // ignore programmatic display updates (e.g. "Enter")
          String t = keyField.getText().trim();
          keyCode = t.length() == 1 ? KeyEvent.getExtendedKeyCodeForChar(t.charAt(0)) : KeyEvent.VK_UNDEFINED;
          syncSpecialRadios(keyCode);  // a typed character is never a special key → clears the radios
          updateResult();
        }
      });

      JPanel modifiers = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
      for (JCheckBox box : new JCheckBox[]{control, option, shift, command}) {
        box.addActionListener(e -> updateResult());
        modifiers.add(box);
      }
      preview.setFont(preview.getFont().deriveFont(Font.BOLD));

      // Radios for keys that can't be pressed into the field (e.g. ⌘Esc): selecting one sets that key.
      JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
      keyRow.add(keyField);
      specialRadios = new JRadioButton[SPECIAL_CODES.length];
      for (int i = 0; i < SPECIAL_CODES.length; i++) {
        int vk = SPECIAL_CODES[i];
        JRadioButton radio = new JRadioButton(SPECIAL_SYMBOLS[i]);
        radio.setToolTipText(KeyEvent.getKeyText(vk));
        radio.addActionListener(e -> { setKey(vk); updateResult(); });
        specialGroup.add(radio);
        specialRadios[i] = radio;
        keyRow.add(radio);
      }

      FormBuilder form = FormBuilder.createFormBuilder()
        .addLabeledComponent("Current:", new JBLabel(KeymapUtil.getKeystrokeText(original)))
        .addLabeledComponent("Key:", keyRow)
        .addLabeledComponent("Modifiers:", modifiers)
        .addLabeledComponent("New:", preview)
        .addComponent(status)
        .addComponent(new JBLabel(affected + " mapping(s) on this key will move."));
      if (notice != null) {
        JBLabel note = new JBLabel(notice);
        note.setForeground(UIUtil.getContextHelpForeground());
        form.addComponent(note);
      }
      return form.getPanel();
    }

    /** Grab a modifier combo (e.g. ⌃X, ⌃↩): set all modifier boxes and the key from the event. */
    private void capture(KeyEvent e) {
      if (isModifierKey(e.getKeyCode())) return;  // still composing the chord
      int mods = e.getModifiersEx() & MODIFIER_MASK;
      control.setSelected((mods & InputEvent.CTRL_DOWN_MASK) != 0);
      option.setSelected((mods & InputEvent.ALT_DOWN_MASK) != 0);
      shift.setSelected((mods & InputEvent.SHIFT_DOWN_MASK) != 0);
      command.setSelected((mods & InputEvent.META_DOWN_MASK) != 0);
      int code = keyCodeOf(e);
      if (code != KeyEvent.VK_UNDEFINED) setKey(code);
      updateResult();  // always refresh — the key text may be unchanged while only modifiers moved
    }

    private void prefill() {
      int m = original.getModifiers();
      control.setSelected((m & InputEvent.CTRL_DOWN_MASK) != 0);
      option.setSelected((m & InputEvent.ALT_DOWN_MASK) != 0);
      shift.setSelected((m & InputEvent.SHIFT_DOWN_MASK) != 0);
      command.setSelected((m & InputEvent.META_DOWN_MASK) != 0);
      setKey(original.getKeyCode());
      updateResult();
    }

    /** Record the key, show its name/symbol, and sync the special radios — without re-parsing. */
    private void setKey(int code) {
      keyCode = code;
      settingText = true;
      keyField.setText(displayFor(code));
      settingText = false;
      syncSpecialRadios(code);
    }

    private void syncSpecialRadios(int code) {
      for (int i = 0; i < SPECIAL_CODES.length; i++) {
        if (SPECIAL_CODES[i] == code) { specialRadios[i].setSelected(true); return; }
      }
      specialGroup.clearSelection();
    }

    private static String displayFor(int code) {
      for (int i = 0; i < SPECIAL_CODES.length; i++) {
        if (SPECIAL_CODES[i] == code) return SPECIAL_SYMBOLS[i];
      }
      return KeyEvent.getKeyText(code);
    }

    private static int keyCodeOf(KeyEvent e) {
      int code = e.getKeyCode();
      if (code != KeyEvent.VK_UNDEFINED) return code;
      char kc = e.getKeyChar();  // some layouts report the char but no VK (e.g. German Ü)
      return kc == KeyEvent.CHAR_UNDEFINED ? KeyEvent.VK_UNDEFINED : KeyEvent.getExtendedKeyCodeForChar(kc);
    }

    private void updateResult() {
      KeyStroke ks = build();
      preview.setText(ks == null ? "—" : KeymapUtil.getKeystrokeText(ks));
      updateStatus(ks);
    }

    /** Green when the shortcut is clear, red when it hits macOS or is already used in this keymap. */
    private void updateStatus(@Nullable KeyStroke ks) {
      if (ks == null) {
        status.setText(" ");
        return;
      }
      List<String> clash = keymapCollisions(ks);
      if (!clash.isEmpty()) {
        status.setText("⚠ Already used in this keymap by " + actionName(clash.get(0))
          + (clash.size() > 1 ? " +" + (clash.size() - 1) + " more" : "") + ".");
        status.setForeground(warnColour());
        return;
      }
      if (system == null) {
        status.setText("macOS check unavailable on this runtime.");
        status.setForeground(UIUtil.getContextHelpForeground());
        return;
      }
      List<ConflictScan.SystemShortcut> macOs = system.get(ks);
      if (macOs == null) {
        status.setText("✓ No conflict.");
        status.setForeground(okColour());
        return;
      }
      ConflictAdvice.Advice advice = ConflictAdvice.resolve(macOs, ks);
      if (advice.category() == ConflictAdvice.Category.DELIBERATE) {
        status.setText("✓ Overlaps a macOS shortcut but keeps working.");
        status.setForeground(okColour());
      }
      else {
        status.setText("⚠ Conflicts with macOS — this shortcut may not work.");
        status.setForeground(warnColour());
      }
    }

    /** Actions already bound to {@code ks} in this keymap, minus the ones being moved off the old key. */
    private List<String> keymapCollisions(KeyStroke ks) {
      Set<String> moving = new HashSet<>(List.of(keymap.getActionIds(original)));
      List<String> hits = new ArrayList<>();
      for (String id : keymap.getActionIds(ks)) {
        if (!moving.contains(id)) hits.add(id);
      }
      return hits;
    }

    private static String actionName(String id) {
      AnAction action = ActionManager.getInstance().getAction(id);
      String text = action == null ? null : action.getTemplateText();
      return text != null && !text.isBlank() ? text : id;
    }

    private @Nullable KeyStroke build() {
      if (keyCode == KeyEvent.VK_UNDEFINED) return null;
      int mods = (control.isSelected() ? InputEvent.CTRL_DOWN_MASK : 0)
               | (option.isSelected() ? InputEvent.ALT_DOWN_MASK : 0)
               | (shift.isSelected() ? InputEvent.SHIFT_DOWN_MASK : 0)
               | (command.isSelected() ? InputEvent.META_DOWN_MASK : 0);
      return KeyStroke.getKeyStroke(keyCode, mods);
    }

    @Override
    protected void doOKAction() {
      KeyStroke ks = build();
      if (ks == null) { setErrorText("Press or type a key.", keyField); return; }
      result = ks;
      super.doOKAction();
    }

    @Nullable KeyStroke getResult() {
      return result;
    }

    private static boolean isModifierKey(int code) {
      return code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
          || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH;
    }

    private void limitToOneChar(JBTextField field) {
      ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
          throws BadLocationException {
          replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
          throws BadLocationException {
          if (settingText || text == null || text.isEmpty()) {
            super.replace(fb, offset, length, text, attr);  // programmatic display (e.g. "Enter") passes through
            return;
          }
          String last = text.substring(text.length() - 1);   // manual typing keeps a single character
          super.replace(fb, 0, fb.getDocument().getLength(), last, attr);
        }
      });
    }
  }
}
