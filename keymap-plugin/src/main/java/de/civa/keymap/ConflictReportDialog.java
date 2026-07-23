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
import com.intellij.openapi.actionSystem.ToggleAction;
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
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.Box;
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
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
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
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
 *   <li><b>Details pane</b> — a {@link Tree} navigating the sections (this keymap's macOS conflicts;
 *       duplicate shortcuts it introduced; window-switch overlaps IntelliJ ignores; benign
 *       double-bound keys) beside a detail view that explains the selected row, lists its actions
 *       interactively (click a name to rebind it, or "Rebind…" to multi-select), and offers the
 *       fix links.</li>
 * </ol>
 */
public final class ConflictReportDialog extends DialogWrapper {
  /** Keymaps this plugin ships — grouped just below the active one in the selector. */
  private static final Set<String> OWN_KEYMAP_NAMES = Set.of("MacBook Pro DE");

  /** Official keymap names the platform reserves; reusing one (or a "Mac OS X" prefix) changes how
   *  shortcuts are interpreted. Mirrors the canonical list in KeymapImpl.kt (notifyAboutMissingKeymap). */
  private static final Set<String> RESERVED_KEYMAP_NAMES = Set.of(
    "Eclipse", "Emacs", "NetBeans 6.5", "QtCreator", "ReSharper", "Sublime Text",
    "Visual Studio", "Visual Assist", "Xcode", "Rider", "VSCode", "macOS System Shortcuts");

  private final Project project;
  private Keymap keymap;   // the keymap the report currently previews (the combo selection)
  private Keymap active;   // the keymap actually active in the IDE (moves on Activate)
  private ConflictScan scan;
  private Tree tree;
  private JPanel detailPanel;         // Swing detail view (replaces the old HTML editor pane)
  private JBScrollPane detailScroll;  // scroll host, also the width source for wrapped text
  private Object currentPayload;      // the tree row the detail currently shows (for re-render)
  private boolean showActionIds;      // gear toggle: show the internal action id next to each name
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
    setTitle("Manage Keymap Conflicts");
    setResizable(true);
    init();
  }

  // Section titles (used both as node labels and to spot the low-priority, collapsed-by-default ones).
  private static final String SEC_KEYMAP = "Keymap conflicts";
  private static final String SEC_IDEA_IGNORED = "Overlaps IntelliJ doesn't flag";
  private static final String SEC_DOUBLE = "Double-bound keys — informational, not conflicts";

  /** macOS shortcut ids IntelliJ's own keymap tool deliberately excludes from its conflict banner. */
  private static final Set<String> IDEA_IGNORED_IDS = Set.of("FocusNextApplicationWindow", "FocusPreviousApplicationWindow");

  /** Typed tree payloads; the renderer and detail pane switch on these. */
  private record Section(String title, int count) {}
  private record Empty(String message) {}
  private record KeymapItem(ConflictScan.ExternalConflict c) {}
  private record IdeaIgnoredItem(ConflictScan.ExternalConflict c) {}

  @Override
  protected JComponent createCenterPanel() {
    tree = new Tree(new DefaultTreeModel(buildRoot()));
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new Renderer());
    tree.addTreeSelectionListener(e -> showDetail(payloadOf(e.getNewLeadSelectionPath())));

    detailPanel = new DetailPanel();
    detailPanel.setBorder(JBUI.Borders.empty(8, 10));
    detailPanel.setBackground(UIUtil.getPanelBackground());
    detailScroll = new JBScrollPane(detailPanel);
    detailScroll.setBorder(JBUI.Borders.empty());
    detailScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    // Wrapped-text blocks size to the viewport width, so re-render when the split moves. Guard on an
    // actual width change so a scrollbar appearing/disappearing can't drive a re-render loop.
    detailScroll.getViewport().addComponentListener(new ComponentAdapter() {
      private int lastWidth = -1;
      @Override public void componentResized(ComponentEvent e) {
        int w = detailScroll.getViewport().getWidth();
        if (w == lastWidth) return;
        lastWidth = w;
        if (currentPayload != null) showDetail(currentPayload);
      }
    });

    expandActionableSections();
    selectFirstConflict();

    // Details pane: the tree navigator and its per-row explanation.
    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.44f);
    splitter.setFirstComponent(new JBScrollPane(tree));
    splitter.setSecondComponent(detailScroll);

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

    // Gear menu, like IDEA's scheme actions. A small drop triangle sits diagonally off the gear's
    // bottom-right corner — the icon is drawn on a slightly enlarged canvas so the triangle lands in
    // the corner with a gap instead of overlapping the gear teeth. Marks this as a menu button.
    Icon gearImg = AllIcons.General.GearPlain;
    Icon triImg = AllIcons.General.ButtonDropTriangle;
    int pad = JBUI.scale(4);
    LayeredIcon gearIcon = new LayeredIcon(2);
    gearIcon.setIcon(gearImg, 0, 0, 0);
    gearIcon.setIcon(triImg, 1, gearImg.getIconWidth() + pad - triImg.getIconWidth(),
                                gearImg.getIconHeight() + pad - triImg.getIconHeight());
    InplaceButton[] gear = new InplaceButton[1];
    gear[0] = new InplaceButton("Keymap actions", gearIcon, e -> showGearMenu(gear[0]));

    JButton activate = new JButton("Activate");
    activate.addActionListener(e -> activateSelected());
    JButton reset = new JButton("Reset");
    reset.addActionListener(e -> keymapCombo.setSelectedItem(active));
    buttonRow = centered(activate, reset);
    buttonRow.setVisible(false);

    // Help button: a slightly larger, blue "?" pinned to the right of the keymap row.
    Icon helpIcon = IconUtil.scale(
      IconUtil.colorize(AllIcons.General.ContextHelp,
        new JBColor(new Color(0x1E7BFF), new Color(0x4C9DFF)), false, false), null, 1.3f);
    InplaceButton help = new InplaceButton("What this plugin does", helpIcon, e -> showHelp());
    JPanel helpHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
    helpHolder.add(help);
    JPanel keymapRow = new JPanel(new BorderLayout());
    keymapRow.add(centered(new JBLabel("Keymap:"), keymapCard, gear[0]), BorderLayout.CENTER);
    keymapRow.add(helpHolder, BorderLayout.EAST);

    JPanel selector = new JPanel();
    selector.setLayout(new BoxLayout(selector, BoxLayout.Y_AXIS));
    selector.setBorder(JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(8, 12)));
    selector.add(keymapRow);
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
    group.addSeparator();
    group.add(new ToggleAction("Show Action IDs") {
      @Override public boolean isSelected(@NotNull AnActionEvent e) { return showActionIds; }
      @Override public void setSelected(@NotNull AnActionEvent e, boolean state) {
        showActionIds = state;
        if (currentPayload != null) showDetail(currentPayload);  // re-render detail pane with/without ids
      }
      @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    });
    ActionManager.getInstance().createActionPopupMenu("ManageKeymapConflictsGear", group)
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
    if (isReservedKeymapName(newName)) {
      Messages.showErrorDialog(project,
        "“" + newName + "” is a reserved keymap name (it starts with “Mac OS X” or matches a built-in "
        + "keymap), which would change how shortcuts are interpreted. Choose a different name.", "Rename Keymap");
      renameField.requestFocusInWindow();
      return;
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

  /** A name the platform reserves: the modifier-converting "Mac OS X" prefix, or an official keymap
   *  name (including its "… OSX" / "… (Mac OS X)" variants). */
  private static boolean isReservedKeymapName(String name) {
    if (name.startsWith("Mac OS X")) return true;
    for (String base : RESERVED_KEYMAP_NAMES) {
      if (name.equals(base) || name.equals(base + " OSX") || name.equals(base + " (Mac OS X)")) return true;
    }
    return false;
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
  private void rebindActions(KeyStroke oldFirst, List<String> ids) {
    if (ids.isEmpty()) return;
    String notice = keymap.canModify() ? null
      : "“" + keymap.getPresentableName() + "” is read-only — an editable copy will be created and activated.";
    ShortcutInputDialog dialog = new ShortcutInputDialog(project, keymap, oldFirst, ids, notice);
    if (!dialog.showAndGet()) return;
    KeyStroke newFirst = dialog.getResult();
    if (newFirst == null || newFirst.equals(oldFirst)) return;
    List<String> moveIds = dialog.selectedIds();  // the user may have unticked some in the dialog
    if (moveIds.isEmpty()) return;

    Keymap target = ensureEditable();
    if (target == null) return;

    // Collect first, mutate second: removeShortcut/addShortcut change what getShortcuts returns.
    record Move(String id, KeyboardShortcut from, KeyboardShortcut to) {}
    List<Move> moves = new ArrayList<>();
    for (String id : moveIds) {
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

    // Window-switching overlaps IntelliJ's own tool ignores go in a low-priority section; the rest
    // are this keymap's conflicts (its whole effective set — inherited bindings included).
    List<ConflictScan.ExternalConflict> normal = scan.keymapConflicts.stream().filter(c -> !ideaIgnored(c)).toList();
    List<ConflictScan.ExternalConflict> ignored = scan.keymapConflicts.stream().filter(c -> ideaIgnored(c)).toList();

    DefaultMutableTreeNode keymapNode = new DefaultMutableTreeNode(new Section(SEC_KEYMAP, normal.size()));
    if (!scan.jbrApiAvailable) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("macOS scan unavailable on this runtime.")));
    }
    else if (normal.isEmpty()) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("No conflicts with macOS. Nothing to change.")));
    }
    for (ConflictScan.ExternalConflict c : normal) keymapNode.add(new DefaultMutableTreeNode(new KeymapItem(c)));
    root.add(keymapNode);

    // "Overlaps IntelliJ doesn't flag": window-switch overlaps IntelliJ gets first, plus the curated
    // SUPPLEMENT notes (macOS features the live scan can't see) — all keep working, shown for completeness.
    List<ConflictAdvice.Supplement> supplements = scan.ownKeymap ? ConflictAdvice.SUPPLEMENT : List.of();
    if (!ignored.isEmpty() || !supplements.isEmpty()) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(
        new Section(SEC_IDEA_IGNORED, ignored.size() + supplements.size()));
      for (ConflictScan.ExternalConflict c : ignored) node.add(new DefaultMutableTreeNode(new IdeaIgnoredItem(c)));
      for (ConflictAdvice.Supplement s : supplements) node.add(new DefaultMutableTreeNode(s));
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

  /** Clear every matching binding on {@code first} for the given actions, copying a read-only keymap first. */
  private void removeShortcutFrom(KeyStroke first, List<String> ids) {
    Keymap target = ensureEditable();
    if (target == null) return;
    for (String id : ids) {
      for (Shortcut sc : target.getShortcuts(id)) {
        if (sc instanceof KeyboardShortcut ks && first.equals(ks.getFirstKeyStroke())) {
          target.removeShortcut(id, ks);
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

  /** Short "what this is / isn't" explainer, shown from the help (?) button. */
  private void showHelp() {
    new HelpDialog().show();
  }

  /** A non-editable HTML pane that renders in the UI font (not the default serif) and is transparent. */
  private static JEditorPane htmlPane(String html) {
    JEditorPane pane = new JEditorPane();
    pane.setEditable(false);
    pane.setOpaque(false);
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    pane.setContentType("text/html");
    pane.setFont(UIUtil.getLabelFont());
    pane.setText(html);
    pane.setCaretPosition(0);
    return pane;
  }

  private final class HelpDialog extends DialogWrapper {
    HelpDialog() {
      super(project);
      setTitle("About Manage Keymap Conflicts");
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      JEditorPane pane = htmlPane("<html><body style='margin:0'>"
        + "<h3 style='margin:0 0 4px 0'>What this plugin does</h3>"
        + "<p style='margin:0 0 12px 0'>It helps you <b>find and resolve shortcut conflicts</b> in your "
        + "existing keymaps — overlaps with macOS system shortcuts and duplicate in-keymap bindings. For "
        + "each conflict you can rebind or remove the shortcut in place, and you can <b>export</b> a keymap "
        + "to XML (the whole keymap, or just the conflicting / overlapping mappings). It also bundles the "
        + "<b>MacBook Pro DE</b> keymap as a ready-made starting point.</p>"
        + "<h3 style='margin:0 0 4px 0'>What this plugin does not</h3>"
        + "<p style='margin:0'>It is <b>not a replacement for IntelliJ's built-in Keymap editor</b>. "
        + "Assigning shortcuts to arbitrary actions, browsing the full action list and general keymap "
        + "editing are done in <b>Settings &rarr; Keymap</b>. This plugin focuses on conflict resolution "
        + "and export for keymaps that already exist.</p>"
        + "</body></html>");
      JBScrollPane scroll = new JBScrollPane(pane);
      scroll.setBorder(JBUI.Borders.empty(12));  // margin around the text pane
      scroll.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(250)));
      return scroll;
    }

    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }
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
    int total = scan.keymapConflicts.size();
    long broken = scan.keymapConflicts.stream().filter(c -> needsAttention(c)).count();
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
    for (ConflictScan.ExternalConflict c : scan.keymapConflicts) {
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
    currentPayload = payload;
    detailPanel.removeAll();
    if (payload instanceof Section s) {
      addHtml("<h3 style='margin:0 0 4px 0'>" + escape(s.title()) + "</h3>"
        + grayBlock(escape(sectionBlurb(s.title()))));
    }
    else if (payload instanceof Empty e) {
      addHtml(grayBlock(escape(e.message())));
    }
    else if (payload instanceof KeymapItem ki) {
      buildConflictDetail(ki.c(), null);
    }
    else if (payload instanceof IdeaIgnoredItem ii) {
      buildConflictDetail(ii.c(),
        "IntelliJ's own Keymap settings does <b>not</b> flag this as a conflict — it deliberately "
        + "ignores window-switching overlaps. It is listed here only for completeness; the shortcut "
        + "works. Changing it is an optional, purely cosmetic tidy-up.");
    }
    else if (payload instanceof ConflictAdvice.Supplement s) {
      String note = escape(s.note()) + " It isn't an actual shortcut in this keymap — the scan can't "
        + "see it — so there is nothing here to remove or rebind; it's listed for awareness only.";
      addHtml(shortcutHeader(s.keys())
        + factRow("IntelliJ", escape(s.ideaSide()))
        + factRow("macOS", escape(s.macSide()))
        + callout("What this means", note));
    }
    else if (payload instanceof ConflictScan.InternalConflict c) {
      buildInternalDetail(c);
    }
    detailPanel.revalidate();
    detailPanel.repaint();
    SwingUtilities.invokeLater(() -> detailScroll.getVerticalScrollBar().setValue(0));
  }

  /** A conflict on one keystroke: facts and status on top, the interactive action list and its fix
   *  links in the middle, the advice below. */
  private void buildConflictDetail(ConflictScan.ExternalConflict c, @Nullable String extraNoteHtml) {
    addHtml(shortcutHeader(KeymapUtil.getKeystrokeText(c.stroke()))
      + statusBadge(c)
      + factRow("macOS", escape(macList(c))));
    addBlock(boldLabel("Actions"));
    ActionListView list = new ActionListView(c.stroke(), c.actions());
    detailPanel.add(list);
    addBlock(linksRow(c.stroke(), c.actions(), list));
    String bottom = callout("What to do", escape(c.advice().note()));
    if (extraNoteHtml != null) bottom += callout("Not flagged by IntelliJ", extraNoteHtml);
    addHtml(bottom);
  }

  /** A key bound to several actions in the keymap; interactive so a stacked one can be moved off. */
  private void buildInternalDetail(ConflictScan.InternalConflict c) {
    KeyStroke first = ((KeyboardShortcut) c.shortcut()).getFirstKeyStroke();
    addHtml(shortcutHeader(KeymapUtil.getShortcutText(c.shortcut())) + internalStatus(c));
    addBlock(boldLabel("Actions"));
    ActionListView list = new ActionListView(first, c.actions());
    detailPanel.add(list);
    addBlock(linksRow(first, c.actions(), list));
    addHtml(callout("What to do", escape(internalNote(c))));
  }

  // ---- detail: Swing building blocks ----------------------------------------------------------

  /** Add a component as a left-aligned block, capped so BoxLayout does not stretch it vertically. */
  private void addBlock(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    detailPanel.add(c);
  }

  /**
   * Add a wrapping HTML text block. A {@link JEditorPane} wraps reliably to a fixed width (unlike a
   * JLabel), so text no longer runs off the right edge; a right border keeps a margin from the edge.
   */
  private void addHtml(String bodyHtml) {
    int w = detailContentWidth();
    JEditorPane pane = htmlPane("<html><body style='margin:0'>" + bodyHtml + "</body></html>");
    pane.setBorder(JBUI.Borders.emptyRight(JBUI.scale(12)));  // keep text off the right edge
    pane.setSize(new Dimension(w, Short.MAX_VALUE));           // fix width so the wrapped height is right
    int h = pane.getPreferredSize().height;
    pane.setPreferredSize(new Dimension(w, h));
    pane.setMaximumSize(new Dimension(w, h));
    pane.setAlignmentX(Component.LEFT_ALIGNMENT);
    detailPanel.add(pane);
  }

  /** Content width for a detail text block — the viewport minus the panel's left/right insets. */
  private int detailContentWidth() {
    int vw = detailScroll != null ? detailScroll.getViewport().getWidth() : 0;
    int base = vw > JBUI.scale(80) ? vw : JBUI.scale(430);
    return Math.max(JBUI.scale(140), base - JBUI.scale(20));
  }

  private JComponent boldLabel(String text) {
    JBLabel label = new JBLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    label.setBorder(JBUI.Borders.empty(8, 0, 2, 0));
    return label;
  }

  private String factRow(String label, String valueHtml) {
    return "<div style='margin:3px 0'><span style='color:" + hex(grayColour()) + "'><b>"
      + escape(label) + "</b></span>&nbsp;&nbsp;&nbsp;" + valueHtml + "</div>";
  }

  /** The fix links below the action list, dot-separated. "Rebind…" moves the checked actions (or all,
   *  if none are checked); "Remove all…" clears the shortcut after a confirmation. */
  private JComponent linksRow(KeyStroke stroke, List<ConflictScan.ActionRef> actions, ActionListView list) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
    row.setOpaque(false);
    List<String> allIds = actions.stream().map(ConflictScan.ActionRef::id).toList();
    row.add(new ActionLink("Rebind…", (ActionListener) e -> {
      List<String> checked = list.checkedIds();
      rebindActions(stroke, checked.isEmpty() ? allIds : checked);
    }));
    row.add(dot());
    row.add(new ActionLink("Remove…", (ActionListener) e -> {
      List<String> checked = list.checkedIds();
      confirmRemove(stroke, checked.isEmpty() ? allIds : checked);
    }));
    row.add(dot());
    row.add(new ActionLink("Open Keymap settings", (ActionListener) e -> openKeymapSettings()));
    return row;
  }

  private JComponent dot() {
    JBLabel d = new JBLabel("•");
    d.setForeground(grayColour());
    return d;
  }

  /** Confirm removal; the dialog's checkbox list lets the user fine-tune which actions to clear. */
  private void confirmRemove(KeyStroke stroke, List<String> ids) {
    RemoveConfirmDialog dialog = new RemoveConfirmDialog(stroke, ids);
    if (dialog.showAndGet()) removeShortcutFrom(stroke, dialog.selectedIds());
  }

  /** Remove confirmation: a "Show actions" checkbox on the button row reveals an editable pick list. */
  private final class RemoveConfirmDialog extends DialogWrapper {
    private final KeyStroke stroke;
    private final List<String> ids;
    private ActionCheckboxList checkList;

    RemoveConfirmDialog(KeyStroke stroke, List<String> ids) {
      super(project);
      this.stroke = stroke;
      this.ids = ids;
      setTitle("Remove Shortcut");
      init();
      setResizable(true);  // a resizable window is movable on macOS and can be resized to fit content
      setOKButtonText("Remove");
    }

    @Override
    protected JComponent createCenterPanel() {
      checkList = new ActionCheckboxList(ids);
      checkList.setVisible(false);

      JBLabel summary = new JBLabel();
      summary.setAlignmentX(Component.LEFT_ALIGNMENT);
      checkList.setOnChange(() -> summary.setText(removeSummary()));
      summary.setText(removeSummary());

      JPanel stack = new JPanel();
      stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
      stack.add(summary);
      stack.add(checkList);

      JPanel wrap = new JPanel(new BorderLayout());  // NORTH keeps content pinned to the top-left
      wrap.add(stack, BorderLayout.NORTH);
      return wrap;
    }

    private String removeSummary() {
      int total = ids.size();
      return "Remove “" + KeymapUtil.getKeystrokeText(stroke) + "” from " + checkList.checkedIds().size()
        + " of " + total + " action" + (total == 1 ? "" : "s") + " on this key?";
    }

    @Override
    protected JComponent createDoNotAskCheckbox() {
      JCheckBox show = new JCheckBox("Show actions");
      show.addActionListener(e -> {
        checkList.setVisible(show.isSelected());
        resizeToFit(checkList);
      });
      return show;
    }

    List<String> selectedIds() {
      return checkList.checkedIds();
    }
  }

  /** The action's display name (template text), falling back to its id when unresolved. */
  private static String actionName(String id) {
    AnAction action = ActionManager.getInstance().getAction(id);
    String text = action == null ? null : action.getTemplateText();
    return text != null && !text.isBlank() ? text : id;
  }

  /** The action's providing plugin when worth showing (not the IDE core, not unknown). */
  private static @Nullable String notableSource(ConflictScan.ActionRef a) {
    String s = a.source();
    return s != null && !s.isEmpty() && !"IDE".equals(s) ? s : null;
  }

  private static @Nullable String notableSources(List<ConflictScan.ActionRef> actions) {
    List<String> sources = actions.stream().map(ConflictReportDialog::notableSource)
      .filter(s -> s != null).distinct().toList();
    return sources.isEmpty() ? null : String.join(", ", sources);
  }

  private String internalStatus(ConflictScan.InternalConflict c) {
    return "<div style='margin:0 0 8px 0; color:" + hex(grayColour()) + "'>"
      + c.actions().size() + " actions use this shortcut.</div>";
  }

  private static String internalNote(ConflictScan.InternalConflict c) {
    return "This is usually not a conflict — IntelliJ picks the action that fits where you are (the "
      + "editor, a tool window, a dialog). IntelliJ's own Keymap tool treats these the same way and "
      + "doesn't flag them. It only breaks if two can fire in the same place; rebind or remove one "
      + "here if so.";
  }

  // ---- detail formatting helpers --------------------------------------------------------------

  /** The "Shortcut: ^C" header — label and keystroke on one line, the key a touch larger. */
  private static String shortcutHeader(String key) {
    return "<div style='font-weight:bold; margin:0 0 6px 0'>Shortcut:&nbsp;&nbsp;"
      + "<span style='font-size:13pt'>" + escape(key) + "</span></div>";
  }

  private String statusBadge(ConflictScan.ExternalConflict c) {
    boolean ok = c.advice().category() == ConflictAdvice.Category.DELIBERATE;
    String colour = hex(ok ? okColour() : warnColour());
    String glyph = ok ? "&#10004;" : "&#9888;";  // ✔ / ⚠
    return "<div style='margin:0 0 8px 0; font-weight:bold; color:" + colour + "'>"
      + glyph + "&nbsp; " + escape(status(c)) + "</div>";
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
    // Standard warning amber: a dark goldenrod that stays legible on a light panel, a soft yellow on dark.
    return new JBColor(new Color(0x8A6D00), new Color(0xF2C55C));
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
    if (title.equals(SEC_KEYMAP)) {
      return "Shortcuts in this keymap that overlap a macOS system shortcut. Select one to see whether "
        + "it still works and to rebind or remove it. Bindings that come from another plugin or the IDE "
        + "core are tagged with their source; you can change them here just the same.";
    }
    if (title.equals(SEC_IDEA_IGNORED)) {
      return "Overlaps IntelliJ's own Keymap tool doesn't flag: keys macOS also uses for switching "
        + "windows (IntelliJ gets them first while it's in front), plus a couple of macOS features the "
        + "live scan can't see (the Emoji viewer, Dictation). All keep working and are listed for "
        + "completeness. The window-switch ones can still be changed; the scan-invisible notes cannot.";
    }
    return "One shortcut bound to several actions inside the keymap. Not a conflict — IntelliJ "
      + "picks the action that fits where you are. Shown for reference only.";
  }

  private static String macList(ConflictScan.ExternalConflict c) {
    return String.join("; ", c.macOs().stream().map(ConflictScan.SystemShortcut::label).distinct().toList());
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /** Resize the hosting dialog to fit its current content height — grows on "(show)", shrinks on "(hide)". */
  private static void resizeToFit(Component c) {
    Window win = SwingUtilities.getWindowAncestor(c);
    if (win == null) return;
    win.setMinimumSize(null);  // drop any floor the peer set at first display, so we can shrink
    win.invalidate();
    win.setSize(win.getWidth(), win.getPreferredSize().height);
    win.validate();
  }

  /** An editable checkbox list of actions — all ticked initially; {@link #checkedIds()} gives the picks. */
  private static final class ActionCheckboxList extends JPanel {
    private final List<String> ids;
    private final List<JCheckBox> boxes = new ArrayList<>();
    private Runnable onChange;

    ActionCheckboxList(java.util.Collection<String> actionIds) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);
      setBorder(JBUI.Borders.empty(4, 6, 0, 0));
      setAlignmentX(Component.LEFT_ALIGNMENT);
      ids = actionIds.stream().sorted().toList();
      for (String id : ids) {
        JCheckBox cb = new JCheckBox(actionName(id), true);
        cb.setOpaque(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.addItemListener(e -> { if (onChange != null) onChange.run(); });
        boxes.add(cb);
        add(cb);
      }
    }

    void setOnChange(Runnable r) { onChange = r; }

    List<String> checkedIds() {
      List<String> result = new ArrayList<>();
      for (int i = 0; i < ids.size(); i++) if (boxes.get(i).isSelected()) result.add(ids.get(i));
      return result;
    }
  }

  @Override
  protected com.intellij.openapi.ui.DialogWrapper.@Nullable DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  /** Detail-pane host that never grows wider than the viewport, so wrapped text can't be clipped. */
  private static final class DetailPanel extends JPanel implements Scrollable {
    DetailPanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return JBUI.scale(16); }
    @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return r.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
  }

  // ---- interactive action list ----------------------------------------------------------------

  /**
   * The actions on one keystroke, shown as a checkbox list. Each action name is a link: hovering
   * highlights it and shows the action's description as a tooltip, clicking rebinds that one action.
   * The checkboxes feed the links-row "Rebind…" (which moves every checked action at once); the top
   * "Select all" link toggles to "Deselect all" once anything is checked.
   */
  private final class ActionListView extends JPanel {
    private final KeyStroke stroke;
    private final List<ConflictScan.ActionRef> actions;
    private final List<JCheckBox> checks = new ArrayList<>();
    private ActionLink selectToggle;

    ActionListView(KeyStroke stroke, List<ConflictScan.ActionRef> actions) {
      this.stroke = stroke;
      this.actions = actions;
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);
      setAlignmentX(Component.LEFT_ALIGNMENT);
      build();
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private void build() {
      JPanel top = actionRow();
      selectToggle = new ActionLink("Select all", (ActionListener) e -> toggleSelectAll());
      top.add(selectToggle);
      add(top);
      for (ConflictScan.ActionRef a : actions) {
        JPanel r = actionRow();
        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        cb.addItemListener(e -> updateToggleText());
        checks.add(cb);
        r.add(cb);
        r.add(actionLabel(a));
        if (showActionIds) {
          JBLabel id = new JBLabel(a.id());
          id.setForeground(grayColour());
          r.add(id);
        }
        String src = notableSource(a);
        if (src != null) {
          JBLabel s = new JBLabel("(" + src + ")");
          s.setForeground(grayColour());
          r.add(s);
        }
        add(r);
      }
      updateToggleText();
    }

    /** A clickable action name: highlighted on hover (the link), its tooltip the action's description. */
    private ActionLink actionLabel(ConflictScan.ActionRef a) {
      ActionLink link = new ActionLink(a.label(), (ActionListener) e -> rebindActions(stroke, List.of(a.id())));
      String desc = a.description() != null && !a.description().isBlank() ? a.description() : a.id();
      link.setToolTipText(desc);
      return link;
    }

    private void toggleSelectAll() {
      boolean select = !anySelected();
      for (JCheckBox cb : checks) cb.setSelected(select);
    }

    private boolean anySelected() {
      for (JCheckBox cb : checks) if (cb.isSelected()) return true;
      return false;
    }

    private void updateToggleText() {
      selectToggle.setText(anySelected() ? "Deselect all" : "Select all");
    }

    List<String> checkedIds() {
      List<String> ids = new ArrayList<>();
      for (int i = 0; i < actions.size(); i++) {
        if (checks.get(i).isSelected()) ids.add(actions.get(i).id());
      }
      return ids;
    }

    private JPanel actionRow() {
      JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(1)));
      p.setOpaque(false);
      p.setAlignmentX(Component.LEFT_ALIGNMENT);
      return p;
    }
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
        renderConflict(ki.c());
      }
      else if (p instanceof IdeaIgnoredItem ii) {
        renderConflict(ii.c());
      }
      else if (p instanceof ConflictAdvice.Supplement s) {
        setIcon(AllIcons.General.Information);
        append(s.keys(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append("  " + s.macSide(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (p instanceof ConflictScan.InternalConflict c) {
        setIcon(AllIcons.General.Information);
        append(KeymapUtil.getShortcutText(c.shortcut()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append("  " + c.actions().size() + " actions", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }

    private void renderConflict(ConflictScan.ExternalConflict c) {
      setIcon(needsAttention(c) ? AllIcons.General.Warning : AllIcons.General.Information);
      append(KeymapUtil.getKeystrokeText(c.stroke()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      List<ConflictScan.ActionRef> a = c.actions();
      String suffix = a.isEmpty() ? "" : a.get(0).label() + (a.size() > 1 ? " +" + (a.size() - 1) : "");
      append("  " + suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      // Tag bindings coming from another plugin or the IDE core (JTree cells size to content, so the
      // tag is appended inline rather than right-aligned; the detail pane shows it per action).
      String source = notableSources(a);
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
    private final Set<String> movingIds;   // actions being moved off `original` (excluded from the clash check)
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
    private ActionCheckboxList checkList;         // editable pick list; shown only when "Show actions" is ticked

    ShortcutInputDialog(@Nullable Project project, Keymap keymap, KeyStroke original,
                        List<String> movingIds, @Nullable String notice) {
      super(project);
      this.keymap = keymap;
      this.original = original;
      this.movingIds = new HashSet<>(movingIds);
      this.affected = movingIds.size();
      this.notice = notice;
      setTitle("Rebind Shortcut");
      init();
      setResizable(true);  // a resizable window is movable on macOS and can be resized to fit content
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

      checkList = new ActionCheckboxList(movingIds);
      checkList.setVisible(false);
      JBLabel movesCount = new JBLabel();
      checkList.setOnChange(() -> movesCount.setText(
        checkList.checkedIds().size() + " of " + affected + " mapping(s) on this key will move."));
      movesCount.setText(affected + " of " + affected + " mapping(s) on this key will move.");

      FormBuilder form = FormBuilder.createFormBuilder()
        .addLabeledComponent("Current:", new JBLabel(KeymapUtil.getKeystrokeText(original)))
        .addLabeledComponent("Key:", keyRow)
        .addLabeledComponent("Modifiers:", modifiers)
        .addLabeledComponent("New:", preview)
        .addComponent(status)
        .addComponent(movesCount)
        .addComponent(checkList);
      if (notice != null) {
        JBLabel note = new JBLabel(notice);
        note.setForeground(UIUtil.getContextHelpForeground());
        form.addComponent(note);
      }
      JPanel wrap = new JPanel(new BorderLayout());  // NORTH keeps the form pinned to the top-left
      wrap.add(form.getPanel(), BorderLayout.NORTH);
      return wrap;
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
      if (isBarePrintable()) {
        status.setText("⚠ Plain “" + displayFor(keyCode) + "” with no modifier — fires whenever you type it. "
          + "Allowed (IDEA permits it), but usually unintended.");
        status.setForeground(warnColour());
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
      List<String> hits = new ArrayList<>();
      for (String id : keymap.getActionIds(ks)) {
        if (!movingIds.contains(id)) hits.add(id);
      }
      return hits;
    }

    private @Nullable KeyStroke build() {
      if (keyCode == KeyEvent.VK_UNDEFINED) return null;
      int mods = (control.isSelected() ? InputEvent.CTRL_DOWN_MASK : 0)
               | (option.isSelected() ? InputEvent.ALT_DOWN_MASK : 0)
               | (shift.isSelected() ? InputEvent.SHIFT_DOWN_MASK : 0)
               | (command.isSelected() ? InputEvent.META_DOWN_MASK : 0);
      return KeyStroke.getKeyStroke(keyCode, mods);
    }

    private boolean noModifiers() {
      return !control.isSelected() && !option.isSelected() && !shift.isSelected() && !command.isSelected();
    }

    /**
     * True when the assembled shortcut is a single printable character with no modifier (v, c, 1, -,
     * an umlaut, …). Pressing it types the character rather than triggering a command in most
     * contexts. IDEA's own keymap allows this, so it only drives a warning — {@link #doOKAction} still
     * accepts it.
     */
    private boolean isBarePrintable() {
      return keyCode != KeyEvent.VK_UNDEFINED && noModifiers() && isPrintableKeyCode(keyCode);
    }

    private static boolean isPrintableKeyCode(int code) {
      if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) return true;
      if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) return true;
      if (code > 0xFFFF) return true;  // extended char codes (Ä Ö Ü ß …)
      return switch (code) {
        case KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_COMMA,
             KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH, KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE,
             KeyEvent.VK_BACK_QUOTE, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
             KeyEvent.VK_BACK_SLASH -> true;
        default -> false;
      };
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

    /** The actions the user left ticked — the set that actually moves. */
    List<String> selectedIds() {
      return checkList != null ? checkList.checkedIds() : new ArrayList<>(movingIds);
    }

    @Override
    protected JComponent createDoNotAskCheckbox() {
      JCheckBox show = new JCheckBox("Show actions");
      show.addActionListener(e -> {
        checkList.setVisible(show.isSelected());
        resizeToFit(checkList);
      });
      return show;
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
