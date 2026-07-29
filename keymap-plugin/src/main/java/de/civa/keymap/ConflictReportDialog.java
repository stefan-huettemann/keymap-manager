package de.civa.keymap;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
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
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleListCellRenderer;
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
import org.jdom.JDOMException;
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
import javax.swing.SwingConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

  /** The running IDE's product name (e.g. "IntelliJ IDEA", "PyCharm", "Android Studio"), inlined
   *  into user-facing text wherever the {@code {ide}} token appears. */
  private static final String IDE_NAME = ApplicationNamesInfo.getInstance().getFullProductName();

  private final Project project;
  private Keymap keymap;   // the keymap the report currently previews (the combo selection)
  private Keymap active;   // the keymap actually active in the IDE (moves on Activate)
  private ConflictScan scan;
  private Tree tree;
  private JBScrollPane treeScroll;    // scroll host for the navigator; the width source for keycap right-align
  private JPanel detailPanel;         // Swing detail view (replaces the old HTML editor pane)
  private JBScrollPane detailScroll;  // scroll host, also the width source for wrapped text
  private Object currentPayload;      // the tree row the detail currently shows (for re-render)
  private boolean showActionIds;      // gear toggle: show the internal action id next to each name
  private boolean showKeymap;         // gear toggle: show the keymap that defines each binding
  private boolean showModifiedOnly;   // gear toggle: show only the "Modified shortcuts" section
  private final Map<Keymap, Set<String>> ownIdsCache = new HashMap<>();  // per-keymap own declarations (for showKeymap)
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
    setTitle("Keymap Manager");
    setResizable(true);
    init();
  }

  // Section titles (used both as node labels and to spot the low-priority, collapsed-by-default ones).
  private static final String SEC_KEYMAP = "Keymap conflicts";
  private static final String SEC_IDEA_IGNORED = "Overlaps {ide} doesn't flag";
  private static final String SEC_DOUBLE = "Double-bound keys — informational, not conflicts";
  private static final String SEC_MODIFIED = "Modified shortcuts — differ from the parent keymap";
  private static final String SEC_INHERITED = "Inherited Shortcuts";

  /** The theme's link colour ({@code Link.activeForeground}) — used for the blue "?" help icon and the
   *  navigator rows' trailing settings icon, so both read as links rather than decoration. */
  private static final Color LINK_COLOR = JBUI.CurrentTheme.Link.Foreground.ENABLED;

  /** The navigator's rightmost column: the same external-link arrow {@link ActionLink#setExternalLinkIcon}
   *  puts on the "Settings…" links, tinted to {@link #LINK_COLOR}. Clicking it opens the platform Keymap
   *  page at that row's action. */
  private static final Icon SETTINGS_ICON = IconUtil.colorize(AllIcons.Ide.External_link_arrow, LINK_COLOR);

  /** Gap between the keycaps and the settings icon column. */
  private static final int ICON_GAP = 10;

  /**
   * The navigator cell's right inset — the margin between the settings icon and the viewport edge. The
   * renderer's border, the click hit test and the icon tooltip all measure the icon column from it, so it
   * lives in one place.
   * <p>Sized to clear a <b>vertical scrollbar</b>: with one showing, 10px left the icon looking crammed
   * against the track (and on macOS, where an overlay scrollbar paints <i>over</i> the viewport rather
   * than narrowing it, nearly touching the bar itself). It is applied unconditionally — a fixed margin
   * keeps every row's icon at the same x whether the tree scrolls or not, which matters more than
   * reclaiming a few pixels when it doesn't.
   */
  private static final int ROW_RIGHT_INSET = 18;

  /** macOS shortcut ids IntelliJ's own keymap tool deliberately excludes from its conflict banner. */
  private static final Set<String> IDEA_IGNORED_IDS = Set.of("FocusNextApplicationWindow", "FocusPreviousApplicationWindow");

  /** Typed tree payloads; the renderer and detail pane switch on these. */
  private record Section(String title, int count) {}
  private record Subtitle(String sectionTitle) {}   // gray one-liner shown as a section's first child
  /** An empty category's placeholder row. All five categories are always listed, so an empty one has to
   *  say so rather than vanish: {@code message} is the one-liner in the navigator, {@code explanation}
   *  the "what would be here, and why isn't it" text {@link #buildEmptyDetail} shows in the details pane,
   *  and {@code ok} distinguishes good news (nothing to fix) from a problem (the scan couldn't run). */
  private record Empty(String sectionTitle, String message, String explanation, boolean ok) {}
  private record KeymapItem(ConflictScan.ExternalConflict c) {}
  private record IdeaIgnoredItem(ConflictScan.ExternalConflict c) {}
  private record ModifiedItem(ConflictScan.ModifiedBinding m) {}
  private record InheritedItem(ConflictScan.ModifiedBinding m) {}

  /** The action a navigator row is "about" — the one its trailing settings icon opens the platform Keymap
   *  page at. A conflict / double-bound row uses its <b>first</b> action, matching the name and meta the
   *  row already shows ({@link #actionsLabel}, {@link #conflictMeta}). Null for rows that name no single
   *  action (a section title, its subtitle, an empty-category placeholder) — those get no icon. */
  private static @Nullable String rowActionId(@Nullable Object payload) {
    if (payload instanceof KeymapItem ki) return firstActionId(ki.c().actions());
    if (payload instanceof IdeaIgnoredItem ii) return firstActionId(ii.c().actions());
    if (payload instanceof ConflictScan.InternalConflict c) return firstActionId(c.actions());
    if (payload instanceof ModifiedItem mi) return mi.m().action().id();
    if (payload instanceof InheritedItem it) return it.m().action().id();
    if (payload instanceof ConflictAdvice.Supplement s) return s.actionId();
    return null;
  }

  private static @Nullable String firstActionId(List<ConflictScan.ActionRef> actions) {
    return actions.isEmpty() ? null : actions.get(0).id();
  }

  /**
   * The action id to open when a mouse event lands on a row's trailing settings icon, else null.
   * <p>The icon's position is deterministic rather than measured: the renderer sizes every cell to end at
   * the viewport edge and lays the icon out last, inside the cell's 10px right border — so the icon spans
   * the last {@code border + iconWidth} pixels of the row's bounds. That keeps the hit test independent
   * of the renderer's component tree, which is rebuilt on every paint.
   */
  private @Nullable String settingsIconHit(MouseEvent e) {
    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path == null) return null;
    String id = rowActionId(payloadOf(path));
    if (id == null) return null;
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return null;
    int right = bounds.x + bounds.width - JBUI.scale(ROW_RIGHT_INSET);   // the renderer's emptyRight border
    return e.getX() >= right - SETTINGS_ICON.getIconWidth() && e.getX() <= right ? id : null;
  }

  @Override
  protected JComponent createCenterPanel() {
    tree = new Tree(new DefaultTreeModel(buildRoot()));
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(JBUI.scale(24));   // fixed, tall enough for the keycap frames
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new Renderer());
    tree.addTreeSelectionListener(e -> showDetail(payloadOf(e.getNewLeadSelectionPath())));
    // Rows are clamped to the viewport width, so long text ellipsizes; the renderer hands out a tooltip
    // with the full text for exactly those rows, which needs the tree registered with the manager.
    ToolTipManager.sharedInstance().registerComponent(tree);
    // A cell renderer only paints, so the trailing settings icon can't be a real button — the tree
    // itself turns a click in that column into the same "open Keymap settings at this action" call the
    // Settings… links make, and shows a hand cursor over it so it reads as clickable.
    tree.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        String id = settingsIconHit(e);
        if (id != null) openKeymapSettings(id);
      }
    });
    tree.addMouseMotionListener(new MouseAdapter() {
      @Override public void mouseMoved(MouseEvent e) {
        tree.setCursor(settingsIconHit(e) != null
          ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
      }
    });
    treeScroll = new JBScrollPane(tree);
    // Keycaps right-align to the viewport edge, so re-measure node widths when that edge moves. The
    // layout cache caches widths, so nudge the row height to force a recompute (guarded on a real
    // width change so it can't loop). Mirrors the detail pane's width-change re-render below.
    treeScroll.getViewport().addComponentListener(new ComponentAdapter() {
      private int lastWidth = -1;
      @Override public void componentResized(ComponentEvent e) {
        int w = treeScroll.getViewport().getWidth();
        if (w == lastWidth) return;
        lastWidth = w;
        int h = tree.getRowHeight();
        tree.setRowHeight(h + 1);   // change → fires; recompute happens on the restore
        tree.setRowHeight(h);
      }
    });

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
    selectFirstRow();

    // Details pane: the tree navigator and its per-row explanation.
    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.44f);
    splitter.setFirstComponent(treeScroll);
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
      IconUtil.colorize(AllIcons.General.ContextHelp, LINK_COLOR, false, false), null, 1.3f);
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

  /** A real IDEA action menu: Export…/Import… and Duplicate always, Rename/Delete when editable. */
  private void showGearMenu(Component anchor) {
    DefaultActionGroup group = new DefaultActionGroup(
      menuAction("Export…", false, this::openExportDialog),
      menuAction("Import…", false, this::openImportDialog),
      Separator.getInstance(),
      menuAction("Duplicate", false, this::duplicateKeymap),
      menuAction("Rename…", true, this::startRename),
      menuAction("Delete…", true, this::deleteKeymap));
    group.addSeparator();
    group.add(new ToggleAction("Show modified only") {
      @Override public boolean isSelected(@NotNull AnActionEvent e) { return showModifiedOnly; }
      @Override public void setSelected(@NotNull AnActionEvent e, boolean state) {
        showModifiedOnly = state;
        rebuildTree();   // the scan is unchanged; just re-shape the tree to the new filter
      }
      @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    });
    group.add(new ToggleAction("Show Action IDs") {
      @Override public boolean isSelected(@NotNull AnActionEvent e) { return showActionIds; }
      @Override public void setSelected(@NotNull AnActionEvent e, boolean state) {
        showActionIds = state;
        tree.repaint();  // Modified rows show/hide the id
        if (currentPayload != null) showDetail(currentPayload);  // re-render detail pane with/without ids
      }
      @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    });
    group.add(new ToggleAction("Show Keymap") {
      @Override public boolean isSelected(@NotNull AnActionEvent e) { return showKeymap; }
      @Override public void setSelected(@NotNull AnActionEvent e, boolean state) {
        showKeymap = state;   // appends the defining keymap to the id string (comma-separated)
        tree.repaint();
        if (currentPayload != null) showDetail(currentPayload);
      }
      @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    });
    group.addSeparator();
    // Leaves to the platform Keymap UI — the trailing link arrow says so, as on the Settings… links.
    group.add(menuAction("Settings…", false, this::openKeymapSettings, SETTINGS_ICON));
    ActionManager.getInstance().createActionPopupMenu("ManageKeymapConflictsGear", group)
      .getComponent().show(anchor, 0, anchor.getHeight());
  }

  /** A menu action; when {@code editableOnly}, it is hidden unless the selected keymap can be modified. */
  private AnAction menuAction(String text, boolean editableOnly, Runnable run) {
    return menuAction(text, editableOnly, run, null);
  }

  /**
   * As above, with {@code trailingIcon} placed <b>after</b> the item's text via the platform's
   * {@code ActionUtil.SECONDARY_ICON} presentation property — documented there as "the icon that will be
   * placed after the text", and read by {@code ActionMenuItem.getSecondaryIcon()} (the same route Git's
   * "New" badge takes). {@code Presentation.setIcon} would instead put it in the left icon gutter.
   * <p>It is set on each {@code update}, on the event's presentation, so it cannot be lost when the
   * template presentation is cloned for the event.
   */
  private AnAction menuAction(String text, boolean editableOnly, Runnable run, @Nullable Icon trailingIcon) {
    return new AnAction(text) {
      @Override public void actionPerformed(@NotNull AnActionEvent e) { run.run(); }
      @Override public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(!editableOnly || keymap.canModify());
        if (trailingIcon != null) e.getPresentation().putClientProperty(ActionUtil.SECONDARY_ICON, trailingIcon);
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
    rebindActions(oldFirst, ids, false);
  }

  /** Opens the rebind dialog with a suggested shortcut already filled in — the navigator's
   *  "Suggest…" link, so the user still confirms (or edits) via the same OK button as a manual
   *  rebind, just as {@link ShortcutSuggester} intends. */
  private void suggestAndRebind(KeyStroke oldFirst, List<String> ids) {
    rebindActions(oldFirst, ids, true);
  }

  private void rebindActions(KeyStroke oldFirst, List<String> ids, boolean autoSuggest) {
    if (ids.isEmpty()) return;
    String notice = keymap.canModify() ? null
      : "“" + keymap.getPresentableName() + "” is read-only — an editable copy will be created and activated.";
    ShortcutInputDialog dialog = new ShortcutInputDialog(project, keymap, oldFirst, ids, notice);
    if (autoSuggest) dialog.applySuggestion();
    if (!dialog.showAndGet()) return;
    KeyStroke newFirst = dialog.getResult();
    if (newFirst == null || newFirst.equals(oldFirst)) return;
    // Exactly the shortcuts left ticked. By default that is the one on `oldFirst` — the only one this
    // operation ever touched — but another of the action's shortcuts can be ticked to move it too.
    Map<String, List<Shortcut>> moveShortcuts = dialog.pickedShortcuts();
    if (moveShortcuts.isEmpty()) return;

    Keymap target = ensureEditable();
    if (target == null) return;

    // Collect first, mutate second: removeShortcut/addShortcut change what getShortcuts returns.
    record Move(String id, KeyboardShortcut from, KeyboardShortcut to) {}
    List<Move> moves = new ArrayList<>();
    for (Map.Entry<String, List<Shortcut>> e : moveShortcuts.entrySet()) {
      for (Shortcut sc : e.getValue()) {
        // Only a keyboard shortcut can move to a keystroke; a ticked mouse/gesture binding is left alone.
        if (sc instanceof KeyboardShortcut ks) {
          moves.add(new Move(e.getKey(), ks, new KeyboardShortcut(newFirst, ks.getSecondKeyStroke())));
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

    // "Show modified only" hides the conflict sections and leaves just the Modified-shortcuts audit view.
    if (!showModifiedOnly) {
      // Window-switching overlaps IntelliJ's own tool ignores go in a low-priority section; the rest
      // are this keymap's conflicts (its whole effective set — inherited bindings included).
      List<ConflictScan.ExternalConflict> normal = scan.keymapConflicts.stream().filter(c -> !ideaIgnored(c)).toList();
      List<ConflictScan.ExternalConflict> ignored = scan.keymapConflicts.stream().filter(c -> ideaIgnored(c)).toList();

      DefaultMutableTreeNode keymapNode = section(SEC_KEYMAP, normal.size());
      if (!scan.jbrApiAvailable) {
        // Empty for a different reason than "all clear", so it gets its own text and the warning look.
        keymapNode.add(node(new Empty(SEC_KEYMAP, "macOS scan unavailable on this runtime.",
          "The live scan reads the macOS shortcut table through a JetBrains Runtime API this runtime "
          + "doesn't expose, so nothing could be checked — this section is empty because no scan ran, "
          + "not because no shortcut collides. Running {ide} on the JetBrains Runtime (the default) "
          + "brings the scan back.", false)));
      }
      else if (normal.isEmpty()) {
        keymapNode.add(node(emptyRow(SEC_KEYMAP, "No conflicts with macOS. Nothing to change.")));
      }
      for (ConflictScan.ExternalConflict c : normal) keymapNode.add(new DefaultMutableTreeNode(new KeymapItem(c)));
      root.add(keymapNode);

      // "Overlaps IntelliJ doesn't flag": window-switch overlaps IntelliJ gets first, plus the curated
      // SUPPLEMENT notes (macOS features the live scan can't see) — all keep working, shown for completeness.
      List<ConflictAdvice.Supplement> supplements = activeSupplements();
      DefaultMutableTreeNode ign = section(SEC_IDEA_IGNORED, ignored.size() + supplements.size());
      if (ignored.isEmpty() && supplements.isEmpty()) {
        ign.add(node(emptyRow(SEC_IDEA_IGNORED, "No unflagged overlaps. Nothing to review.")));
      }
      for (ConflictScan.ExternalConflict c : ignored) ign.add(new DefaultMutableTreeNode(new IdeaIgnoredItem(c)));
      for (ConflictAdvice.Supplement s : supplements) ign.add(new DefaultMutableTreeNode(s));
      root.add(ign);

      DefaultMutableTreeNode dbl = section(SEC_DOUBLE, scan.internal.size());
      if (scan.internal.isEmpty()) {
        dbl.add(node(emptyRow(SEC_DOUBLE, "No key is bound to more than one action.")));
      }
      for (ConflictScan.InternalConflict c : scan.internal) dbl.add(new DefaultMutableTreeNode(c));
      root.add(dbl);
    }

    // Modified shortcuts — this keymap's own declarations (its diff vs the parent). Always present; the
    // only section when "Show modified only" is on. Informational, collapsed by default otherwise.
    DefaultMutableTreeNode mod = section(SEC_MODIFIED, scan.modified.size());
    if (scan.modified.isEmpty()) {
      mod.add(node(emptyRow(SEC_MODIFIED, "This keymap declares no shortcuts of its own.")));
    }
    for (ConflictScan.ModifiedBinding m : scan.modified) mod.add(new DefaultMutableTreeNode(new ModifiedItem(m)));
    root.add(mod);

    // Inherited shortcuts — effective bindings from the parent chain this keymap doesn't declare itself.
    // Informational, collapsed by default; hidden in "Show modified only" mode. Can be large.
    if (!showModifiedOnly) {
      DefaultMutableTreeNode inh = section(SEC_INHERITED, scan.inherited.size());
      if (scan.inherited.isEmpty()) {
        inh.add(node(emptyRow(SEC_INHERITED, "No inherited shortcuts (this keymap declares them all).")));
      }
      for (ConflictScan.ModifiedBinding m : scan.inherited) inh.add(new DefaultMutableTreeNode(new InheritedItem(m)));
      root.add(inh);
    }

    return root;
  }

  /** The curated {@link ConflictAdvice#SUPPLEMENT} entries that apply to the <b>selected</b> keymap:
   *  those whose IDE action is actually bound here. (Was gated on {@code ownKeymap}, which is why the
   *  "Overlaps…" section showed only for the bundled keymap; keying it to the binding shows it wherever
   *  the action exists — e.g. any keymap that binds {@code RunAnything} to double-⌃.) */
  private List<ConflictAdvice.Supplement> activeSupplements() {
    return ConflictAdvice.SUPPLEMENT.stream()
      .filter(s -> s.actionId() != null && keymap.getShortcuts(s.actionId()).length > 0)
      .toList();
  }

  /** A section node whose first child is its subtitle info-row (the explanation shown above the list
   *  when the category is expanded — spec 0003). Items are added by the caller after this. */
  private DefaultMutableTreeNode section(String title, int count) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Section(title, count));
    node.add(new DefaultMutableTreeNode(new Subtitle(title)));
    return node;
  }

  private static DefaultMutableTreeNode node(Object payload) {
    return new DefaultMutableTreeNode(payload);
  }

  /** The ordinary "this category is empty, and that's fine" placeholder: the explanation comes from
   *  {@link #emptyExplanation}. The one case that needs different wording — the macOS scan not being
   *  available at all — builds its {@link Empty} directly. */
  private static Empty emptyRow(String sectionTitle, String message) {
    return new Empty(sectionTitle, message, emptyExplanation(sectionTitle), true);
  }

  /** Why a category is empty and what would otherwise be listed in it. Every category is always shown
   *  (an empty one must explain itself instead of disappearing), so each needs an answer here. */
  private static String emptyExplanation(String title) {
    if (title.equals(SEC_KEYMAP)) {
      return "Nothing in this keymap collides with a macOS system shortcut, so every binding reaches "
        + "{ide}. Entries appear here when macOS claims a key first — each one then tells you whether "
        + "the {ide} shortcut still works and lets you rebind or remove it. The scan is live, so "
        + "changing a shortcut in System Settings can add or clear entries.";
    }
    if (title.equals(SEC_IDEA_IGNORED)) {
      return "No overlaps of the kind {ide}'s own Keymap tool leaves out of its conflict banner: keys "
        + "macOS also uses for switching windows, plus the few macOS features the live scan can't see "
        + "(the Emoji viewer, Dictation). Anything listed here keeps working — it is shown for "
        + "completeness, and can still be rebound or removed.";
    }
    if (title.equals(SEC_DOUBLE)) {
      return "No key in this keymap reaches more than one action. Entries appear when one keystroke is "
        + "bound to two or more, which is usually harmless — {ide} picks the action that fits where you "
        + "are — so they are listed for reference rather than flagged as conflicts.";
    }
    if (title.equals(SEC_MODIFIED)) {
      return "This keymap declares no shortcut of its own: everything it provides comes from its parent "
        + "keymap unchanged. Your first edit here — a rebind, a removal, or a cleared inherited binding "
        + "— appears in this section, and can be reverted from it.";
    }
    return "This keymap declares every shortcut it provides itself, so there is nothing left to inherit "
      + "from its parents. Actions with no shortcut at all aren't counted here — binding one is a job "
      + "for the Keymap settings.";
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
    ownIdsCache.clear();   // an edit changes own declarations, so drop the Show-Keymap cache
    scan = ConflictScan.of(keymap);
    rebuildTree();
    updateSummary();
  }

  /** Re-shape the tree from the current scan (no re-scan) — used when only the view filter changes. */
  private void rebuildTree() {
    tree.setModel(new DefaultTreeModel(buildRoot()));
    expandActionableSections();
    selectFirstRow();
  }

  /** Expand the sections worth opening: normally the actionable conflict sections (informational ones
   *  stay collapsed); in "Show modified only" mode, the Modified-shortcuts section itself. */
  private void expandActionableSections() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      if (shouldExpand(section.getUserObject())) tree.expandPath(new TreePath(section.getPath()));
    }
  }

  private void selectFirstRow() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      if (!shouldExpand(section.getUserObject())) continue;
      for (int j = 0; j < section.getChildCount(); j++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) section.getChildAt(j);
        if (child.getUserObject() instanceof Subtitle) continue;   // skip the subtitle info-row
        tree.setSelectionPath(new TreePath(child.getPath()));
        return;
      }
    }
  }

  /** Which sections open and hold the initial selection: the Modified section alone when "Show modified
   *  only" is on, otherwise every non-informational (actionable) section. */
  private boolean shouldExpand(Object payload) {
    return isModifiedSection(payload) ? showModifiedOnly : !showModifiedOnly && !isInformationalSection(payload);
  }

  private static boolean isInformationalSection(Object payload) {
    return payload instanceof Section s
      && (s.title().equals(SEC_DOUBLE) || s.title().equals(SEC_IDEA_IGNORED)
          || s.title().equals(SEC_MODIFIED) || s.title().equals(SEC_INHERITED));
  }

  private static boolean isModifiedSection(Object payload) {
    return payload instanceof Section s && s.title().equals(SEC_MODIFIED);
  }

  private @Nullable Object payloadOf(@Nullable TreePath path) {
    return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
  }

  // ---- links ----------------------------------------------------------------------------------

  /**
   * Clear exactly the given shortcuts, per action, copying a read-only keymap first.
   * <p>The single removal path now that the confirm dialog picks shortcuts individually — it replaces the
   * old "clear this one keystroke" / "clear every shortcut" pair, which were two different scopes chosen
   * by the caller. Both are now expressed as which toggles the dialog opens with.
   */
  private void removeShortcuts(Map<String, List<Shortcut>> picked) {
    if (picked.isEmpty()) return;
    Keymap target = ensureEditable();
    if (target == null) return;
    for (Map.Entry<String, List<Shortcut>> e : picked.entrySet()) {
      for (Shortcut sc : e.getValue()) target.removeShortcut(e.getKey(), sc);
    }
    refresh();
    updateActionButtons();
  }

  private void openKeymapSettings() {
    openKeymapSettings(null);
  }

  /**
   * Open the platform Keymap settings, optionally landing on one action.
   * <p>With an {@code actionId} we use the {@code (Project, Class, Consumer)} overload, whose
   * {@code additionalConfiguration} consumer runs <b>before</b> the dialog is shown, and call
   * {@link KeymapPanel#selectAction} — which is documented to remember the id and apply it once the
   * panel finishes initializing, i.e. exactly this case. It reveals and selects the action's tree node.
   * <p>Note there is no public counterpart for the page's <i>find-by-shortcut</i> filter
   * ({@code filterTreeByShortcut} and the filtering panel are private), and the page's text filter
   * matches names/descriptions/ids but <b>not</b> shortcut text — so an action id is the way in.
   */
  private void openKeymapSettings(@Nullable String actionId) {
    Project p = project;
    close(OK_EXIT_CODE);
    ApplicationManager.getApplication().invokeLater(() -> {
      ShowSettingsUtil util = ShowSettingsUtil.getInstance();
      if (actionId == null) util.showSettingsDialog(p, KeymapPanel.class);
      else util.showSettingsDialog(p, KeymapPanel.class, panel -> panel.selectAction(actionId));
    });
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
    pane.setText(subIde(html));
    pane.setCaretPosition(0);
    return pane;
  }

  private final class HelpDialog extends DialogWrapper {
    HelpDialog() {
      super(project);
      setTitle("About Keymap Manager");
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      JEditorPane pane = htmlPane("<html><body style='margin:0'>"
        + "<h3 style='margin:0 0 4px 0'>What this plugin does</h3>"
        + "<p style='margin:0 0 8px 0'>A <b>macOS-focused keymap manager</b>. It shows you what a keymap "
        + "really binds, where those bindings collide with macOS, and lets you change them without leaving "
        + "the report.</p>"
        + "<ul style='margin:0 0 12px 0'>"
        + "<li><b>Find conflicts.</b> A live scan of the macOS system-shortcut table — the same source "
        + "{ide} uses for its own conflict banner — says for every overlap whether {ide} still gets the "
        + "key or macOS takes it first, with advice per case. Alongside it: <b>double-bound keys</b> (one "
        + "keystroke on several actions) and the overlaps {ide}'s own tool doesn't flag.</li>"
        + "<li><b>Edit any bound shortcut in place.</b> <b>Rebind</b>, <b>remove</b> or <b>revert</b> to "
        + "the parent keymap's binding — one action, several at once, or a whole category. Works on the "
        + "keymap's own bindings <i>and</i> on the ones it inherits; editing a read-only keymap derives an "
        + "editable copy first. Rebinding captures a real keypress (even {ide}-bound combos like "
        + "&#8963;X), covers keys you can't press into a field (&#8617; &#9099; &#9003; &#8677; Space), "
        + "handles German keys (&#196; &#214; &#220; &#223;), and flags macOS overlaps and existing "
        + "bindings as you type.</li>"
        + "<li><b>Review what changed.</b> <b>Modified shortcuts</b> lists what this keymap declares "
        + "itself — its diff against the parent — and <b>Inherited shortcuts</b> lists what it takes "
        + "from the parent unchanged. The gear menu can narrow the report to just the modified ones, and "
        + "can append each action's <b>id</b> and the <b>keymap that defines</b> its binding.</li>"
        + "<li><b>Manage keymaps.</b> Pick any installed keymap, then activate, duplicate, rename or "
        + "delete it, and <b>import / export</b> keymaps as XML — the whole inheritance chain, or just "
        + "the conflicts, overlaps, double-bound keys or your changes versus the parent.</li>"
        + "<li><b>Bundled keymap.</b> <b>MacBook Pro DE</b>, tuned for a MacBook Pro with the German "
        + "(T1) layout, where many stock shortcuts are physically unpressable.</li>"
        + "</ul>"
        + "<h3 style='margin:0 0 4px 0'>Where it stops</h3>"
        + "<p style='margin:0 0 8px 0'>It edits every shortcut a keymap <b>binds</b> — but it doesn't "
        + "browse the complete action tree, so giving a shortcut to an action that currently has "
        + "<b>none</b> stays a job for <b>Settings &rarr; Keymap</b>. Every category here links straight "
        + "there, landing on the action you were looking at.</p>"
        + "<p style='margin:0'>The macOS side is not ours to change either: when a system shortcut is the "
        + "one in the way, free the key in <b>System Settings &rarr; Keyboard &rarr; Keyboard "
        + "Shortcuts</b>. And the live scan needs the JetBrains Runtime — on another runtime the report "
        + "still works, but it can't see macOS.</p>"
        + "</body></html>");
      JBScrollPane scroll = new JBScrollPane(pane);
      scroll.setBorder(JBUI.Borders.empty(12));  // margin around the text pane
      scroll.setPreferredSize(new Dimension(JBUI.scale(560), JBUI.scale(420)));
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
    if (!scan.modified.isEmpty()) {
      text += "&nbsp;&nbsp;<span style='color:" + hex(grayColour()) + "'>·&nbsp;&nbsp;"
        + scan.modified.size() + " shortcut(s) modified in this keymap.</span>";
    }
    summaryText.setText("<html>" + text + "</html>");
    summaryIcon.setIcon(icon);
  }

  private static boolean needsAttention(ConflictScan.ExternalConflict c) {
    return c.advice().category() != ConflictAdvice.Category.DELIBERATE;
  }

  // ---- export ---------------------------------------------------------------------------------

  /**
   * A selectable export scope. Categories 1-3 are built from the effective bindings the scan reads
   * (inherited ones included), serialized with the platform's own keystroke writer; category 4 is
   * the keymap's own declarations (its diff against the parent, via {@link KeymapImpl#writeScheme()}).
   */
  private enum ExportCategory {
    CONFLICTS("Conflicts", "conflicts", "macOS takes the key first"),
    OVERLAPS("Overlaps", "overlaps", "overlap macOS but keep working"),
    DOUBLE_BINDS("Double-bound keys", "double-binds", "one key bound to several actions"),
    CHANGES("Changes vs the parent keymap", "changes", "everything this keymap declares itself");

    final String label;
    final String suffix;
    final String hint;
    ExportCategory(String label, String suffix, String hint) {
      this.label = label; this.suffix = suffix; this.hint = hint;
    }
  }

  /** Gear-menu entry point: choose what to export and how it is packaged. */
  private void openExportDialog() {
    ExportDialog dialog = new ExportDialog();
    if (!dialog.showAndGet()) return;
    if (dialog.isAllKeys()) exportAllKeys();
    else exportCategories(dialog.selectedCategories());
  }

  /**
   * Export the chosen categories. A single category is written as one {@code .xml}; two or more are
   * bundled into a {@code .zip} with one XML per category.
   */
  private void exportCategories(Set<ExportCategory> categories) {
    LinkedHashMap<ExportCategory, String> files = new LinkedHashMap<>();
    for (ExportCategory cat : ExportCategory.values()) {
      if (!categories.contains(cat)) continue;
      String xml = buildCategoryXml(cat);
      if (xml != null) files.put(cat, xml);
    }
    if (files.isEmpty()) {
      Messages.showInfoMessage(project, "Nothing to export for the selected options.", "Nothing to Export");
      return;
    }
    String name = sanitize(keymap.getName());
    if (files.size() == 1) {
      ExportCategory cat = files.keySet().iterator().next();
      saveText("keymap-" + name + "-" + cat.suffix + ".xml", files.get(cat));
    }
    else {
      LinkedHashMap<String, String> entries = new LinkedHashMap<>();
      for (Map.Entry<ExportCategory, String> e : files.entrySet()) {
        entries.put(name + "-" + e.getKey().suffix + ".xml", e.getValue());
      }
      saveZip("keymap-" + name + "-export.zip", entries);
    }
  }

  /** The keymap XML for one category, or {@code null} if it has nothing (or cannot be serialized). */
  private @Nullable String buildCategoryXml(ExportCategory cat) {
    if (cat == ExportCategory.CHANGES) {
      if (!(keymap instanceof KeymapImpl impl)) {
        Messages.showErrorDialog(project,
          "“Changes vs the parent keymap” cannot be serialized for this keymap on this runtime.", "Export Failed");
        return null;
      }
      return JDOMUtil.writeElement(impl.writeScheme());  // <keymap ...> with the own <action> declarations
    }
    Map<String, Set<KeyboardShortcut>> byAction = switch (cat) {
      case CONFLICTS -> conflictShortcuts(true);
      case OVERLAPS -> conflictShortcuts(false);
      case DOUBLE_BINDS -> doubleBoundShortcuts();
      default -> Map.of();
    };
    if (byAction.isEmpty()) return null;
    return JDOMUtil.writeElement(keymapElement(keymap.getPresentableName() + " (" + cat.suffix + ")", byAction));
  }

  /** Effective keyboard shortcuts of every action on a macOS-overlapping key, split by whether the
   *  overlap needs attention (macOS wins) or merely overlaps but keeps working. */
  private Map<String, Set<KeyboardShortcut>> conflictShortcuts(boolean needsAttention) {
    Map<String, Set<KeyboardShortcut>> byAction = new TreeMap<>();
    for (ConflictScan.ExternalConflict c : scan.keymapConflicts) {
      if (needsAttention(c) != needsAttention) continue;
      KeyStroke first = c.stroke();
      for (ConflictScan.ActionRef a : c.actions()) {
        for (Shortcut sc : keymap.getShortcuts(a.id())) {
          if (sc instanceof KeyboardShortcut ks && first.equals(ks.getFirstKeyStroke())) {
            byAction.computeIfAbsent(a.id(), k -> new LinkedHashSet<>()).add(ks);
          }
        }
      }
    }
    return byAction;
  }

  /** The shared shortcut of every double-bound action (the in-keymap duplicates), by action. */
  private Map<String, Set<KeyboardShortcut>> doubleBoundShortcuts() {
    Map<String, Set<KeyboardShortcut>> byAction = new TreeMap<>();
    for (ConflictScan.InternalConflict c : scan.internal) {
      if (!(c.shortcut() instanceof KeyboardShortcut ks)) continue;
      for (ConflictScan.ActionRef a : c.actions()) {
        byAction.computeIfAbsent(a.id(), k -> new LinkedHashSet<>()).add(ks);
      }
    }
    return byAction;
  }

  /** Build a {@code <keymap>} element listing the given actions, using the platform's own keystroke
   *  serializer so the format (incl. the German umlaut hex codes) matches written keymaps exactly. */
  private static Element keymapElement(String name, Map<String, Set<KeyboardShortcut>> byAction) {
    Element root = new Element("keymap");
    root.setAttribute("version", "1");
    root.setAttribute("name", name);
    for (Map.Entry<String, Set<KeyboardShortcut>> e : byAction.entrySet()) {
      Element action = new Element("action");
      action.setAttribute("id", e.getKey());
      for (KeyboardShortcut ks : e.getValue()) {
        Element shortcut = new Element("keyboard-shortcut");
        shortcut.setAttribute("first-keystroke", KeyStrokeAdapter.toString(ks.getFirstKeyStroke()));
        KeyStroke second = ks.getSecondKeyStroke();
        if (second != null) shortcut.setAttribute("second-keystroke", KeyStrokeAdapter.toString(second));
        action.addContent(shortcut);
      }
      root.addContent(action);
    }
    return root;
  }

  /** Export the whole inheritance chain — this keymap and every parent down to {@code $default} — as
   *  a {@code .zip} of one XML per level. Each level holds its own declarations, so the set reconstructs
   *  everything ({@code $default} carries the base, since it has no parent). */
  private void exportAllKeys() {
    LinkedHashMap<String, String> entries = new LinkedHashMap<>();
    int level = 1;
    for (Keymap k = keymap; k != null; k = k.getParent(), level++) {
      if (k instanceof KeymapImpl impl) {
        entries.put(String.format("%02d-%s.xml", level, sanitize(k.getName())), JDOMUtil.writeElement(impl.writeScheme()));
      }
    }
    if (entries.isEmpty()) {
      Messages.showErrorDialog(project, "This keymap cannot be serialized on this runtime.", "Export Failed");
      return;
    }
    saveZip("keymap-" + sanitize(keymap.getName()) + "-all.zip", entries);
  }

  /** How many items category {@code cat} would export (drives the dialog's counts). */
  private int categoryCount(ExportCategory cat) {
    return switch (cat) {
      case CONFLICTS -> (int) scan.keymapConflicts.stream().filter(ConflictReportDialog::needsAttention).count();
      case OVERLAPS -> (int) scan.keymapConflicts.stream().filter(c -> !needsAttention(c)).count();
      case DOUBLE_BINDS -> scan.internal.size();
      case CHANGES -> keymap instanceof KeymapImpl impl ? impl.writeScheme().getChildren("action").size() : 0;
    };
  }

  private void saveText(String defaultName, String content) {
    File file = chooseSaveFile(defaultName, "xml");
    if (file == null) return;
    try {
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }
    catch (IOException ex) {
      Messages.showErrorDialog(project, "Could not write the file:\n" + ex.getMessage(), "Export Failed");
      return;
    }
    openOrNotify(file);
  }

  private void saveZip(String defaultName, Map<String, String> entries) {
    File file = chooseSaveFile(defaultName, "zip");
    if (file == null) return;
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      for (Map.Entry<String, String> e : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    catch (IOException ex) {
      Messages.showErrorDialog(project, "Could not write the archive:\n" + ex.getMessage(), "Export Failed");
      return;
    }
    openOrNotify(file);
  }

  private @Nullable File chooseSaveFile(String defaultName, String extension) {
    FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Keymap", "Save the export", extension);
    FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
    VirtualFileWrapper wrapper = dialog.save(defaultName);
    return wrapper == null ? null : wrapper.getFile();
  }

  /** Open an exported XML in the editor; for archives just report where it landed. */
  private void openOrNotify(File file) {
    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (project != null && vf != null && "xml".equalsIgnoreCase(vf.getExtension())) {
      FileEditorManager.getInstance(project).openFile(vf, true);
    }
    else {
      Messages.showInfoMessage(project, "Saved to:\n" + file.getAbsolutePath(), "Export Complete");
    }
  }

  private static String sanitize(String s) {
    String cleaned = s.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return cleaned.isEmpty() ? "keymap" : cleaned;
  }

  /** Chooses the export scope: the whole inheritance chain, or any mix of the four categories. */
  private final class ExportDialog extends DialogWrapper {
    private final JRadioButton selectedRadio = new JRadioButton("Selected mappings", true);
    private final JRadioButton allRadio =
      new JRadioButton("All keys — this keymap and every parent down to $default (.zip)");
    private final Map<ExportCategory, JCheckBox> boxes = new EnumMap<>(ExportCategory.class);
    private final Map<ExportCategory, Integer> counts = new EnumMap<>(ExportCategory.class);

    ExportDialog() {
      super(project);
      setTitle("Export Keymap");
      init();
      setOKButtonText("Export");
      updateOkState();
    }

    @Override
    protected JComponent createCenterPanel() {
      for (ExportCategory cat : ExportCategory.values()) counts.put(cat, categoryCount(cat));

      ButtonGroup mode = new ButtonGroup();
      mode.add(selectedRadio);
      mode.add(allRadio);
      selectedRadio.addActionListener(e -> { syncEnabled(); updateOkState(); });
      allRadio.addActionListener(e -> { syncEnabled(); updateOkState(); });

      JPanel categories = new JPanel();
      categories.setLayout(new BoxLayout(categories, BoxLayout.Y_AXIS));
      categories.setBorder(JBUI.Borders.emptyLeft(22));
      for (ExportCategory cat : ExportCategory.values()) {
        int count = counts.get(cat);
        JCheckBox box = new JCheckBox(cat.label + " (" + count + ") — " + cat.hint);
        box.setEnabled(count > 0);
        box.addActionListener(e -> updateOkState());
        boxes.put(cat, box);
        categories.add(box);
      }

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(JBUI.Borders.empty(10, 12));
      panel.add(selectedRadio);
      panel.add(categories);
      panel.add(Box.createVerticalStrut(JBUI.scale(8)));
      panel.add(allRadio);
      return panel;
    }

    private void syncEnabled() {
      boolean selectable = selectedRadio.isSelected();
      boxes.forEach((cat, box) -> box.setEnabled(selectable && counts.get(cat) > 0));
    }

    private void updateOkState() {
      setOKActionEnabled(allRadio.isSelected() || !selectedCategories().isEmpty());
    }

    boolean isAllKeys() {
      return allRadio.isSelected();
    }

    Set<ExportCategory> selectedCategories() {
      Set<ExportCategory> result = EnumSet.noneOf(ExportCategory.class);
      if (selectedRadio.isSelected()) {
        boxes.forEach((cat, box) -> { if (box.isEnabled() && box.isSelected()) result.add(cat); });
      }
      return result;
    }
  }

  // ---- import ---------------------------------------------------------------------------------

  /** One action's imported bindings; an empty list is a clearing override (an empty {@code <action/>}). */
  private record ImportEntry(String actionId, List<Shortcut> shortcuts) {}

  /** The result of parsing a keymap file: its declared name and parent (if any), the action
   *  declarations to replay, a shortcut tally, and any non-fatal notes to show the user. */
  private record ParsedImport(String name, @Nullable String parentName,
                              List<ImportEntry> entries, int shortcutCount, List<String> warnings) {}

  /** Gear-menu entry point: pick a keymap XML, validate it, resolve its name and parent, then preview
   *  the result. The imported keymap is editable, so the gear menu's Delete… removes it again. */
  private void openImportDialog() {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
      .withTitle("Import Keymap");
    VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
    if (file == null) return;

    ParsedImport parsed = parseImport(file);
    if (parsed == null) return;  // parseImport already reported why

    ImportDialog dialog = new ImportDialog(parsed);
    if (!dialog.showAndGet()) return;
    performImport(dialog.chosenName(), dialog.chosenParent(), parsed.entries());
  }

  /** Read and validate a keymap file. Returns {@code null} (after showing why) on a fatal problem —
   *  unreadable file, malformed XML, a root that isn't {@code <keymap>}, or no actions at all.
   *  Individual unreadable or unsupported shortcut elements are collected as warnings, not fatal. */
  private @Nullable ParsedImport parseImport(VirtualFile file) {
    String text;
    try {
      text = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
    }
    catch (IOException ex) {
      Messages.showErrorDialog(project, "Could not read the file:\n" + ex.getMessage(), "Import Failed");
      return null;
    }
    Element root;
    try {
      root = JDOMUtil.load(text);
    }
    catch (IOException | JDOMException ex) {
      Messages.showErrorDialog(project,
        "This file is not well-formed XML, so it can't be a keymap:\n" + ex.getMessage(), "Import Failed");
      return null;
    }
    if (!"keymap".equals(root.getName())) {
      Messages.showErrorDialog(project,
        "This is not a keymap file — its root element is <" + root.getName() + ">, not <keymap>.", "Import Failed");
      return null;
    }
    String name = root.getAttributeValue("name");
    if (name == null || name.isBlank()) name = file.getNameWithoutExtension();
    String parentName = root.getAttributeValue("parent");

    List<ImportEntry> entries = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    int shortcutCount = 0;
    for (Element action : root.getChildren("action")) {
      String id = action.getAttributeValue("id");
      if (id == null || id.isBlank()) { warnings.add("An <action> without an id was skipped."); continue; }
      List<Shortcut> shortcuts = new ArrayList<>();
      for (Element sc : action.getChildren()) {
        Shortcut shortcut = parseShortcut(sc, id, warnings);
        if (shortcut != null) shortcuts.add(shortcut);
      }
      shortcutCount += shortcuts.size();
      entries.add(new ImportEntry(id, shortcuts));  // an empty list clears the action (an empty <action/>)
    }
    if (entries.isEmpty()) {
      Messages.showErrorDialog(project,
        "This keymap file declares no actions, so there is nothing to import.", "Import Failed");
      return null;
    }
    return new ParsedImport(name.trim(), parentName, entries, shortcutCount, warnings);
  }

  /** Parse one shortcut element the way the platform's own keymap reader does (same public APIs).
   *  Unknown or unreadable elements are noted in {@code warnings} and dropped rather than aborting. */
  private static @Nullable Shortcut parseShortcut(Element sc, String actionId, List<String> warnings) {
    try {
      switch (sc.getName()) {
        case "keyboard-shortcut" -> {
          KeyStroke first = KeyStrokeAdapter.getKeyStroke(sc.getAttributeValue("first-keystroke"));
          if (first == null) { warnings.add("Unreadable keyboard shortcut on “" + actionId + "” was skipped."); return null; }
          String secondStr = sc.getAttributeValue("second-keystroke");
          KeyStroke second = secondStr == null ? null : KeyStrokeAdapter.getKeyStroke(secondStr);
          return new KeyboardShortcut(first, second);
        }
        case "mouse-shortcut" -> {
          return KeymapUtil.parseMouseShortcut(sc.getAttributeValue("keystroke"));
        }
        case "keyboard-gesture-shortcut" -> {
          KeyStroke stroke = KeyStrokeAdapter.getKeyStroke(sc.getAttributeValue("keystroke"));
          KeyboardGestureAction.ModifierType modifier = gestureModifier(sc.getAttributeValue("modifier"));
          if (stroke == null || modifier == null) { warnings.add("Unreadable gesture shortcut on “" + actionId + "” was skipped."); return null; }
          return KeyboardModifierGestureShortcut.newInstance(modifier, stroke);
        }
        default -> {
          warnings.add("Unsupported <" + sc.getName() + "> element under “" + actionId + "” was skipped.");
          return null;
        }
      }
    }
    catch (RuntimeException ex) {  // a malformed keystroke/modifier string — skip just this shortcut
      warnings.add("A shortcut on “" + actionId + "” could not be read and was skipped.");
      return null;
    }
  }

  /** Match a gesture {@code modifier} attribute ("dblClick"/"hold") to its enum value. */
  private static @Nullable KeyboardGestureAction.ModifierType gestureModifier(@Nullable String value) {
    if (value == null) return null;
    for (KeyboardGestureAction.ModifierType m : KeyboardGestureAction.ModifierType.values()) {
      if (m.toString().equals(value) || m.name().equals(value)) return m;
    }
    return null;
  }

  /** Build the imported keymap as a child of the chosen parent, replaying its action declarations
   *  (each replaces the parent's binding for that action; an empty declaration clears it), register
   *  it, and preview it in the report. Not activated — like Duplicate, the active keymap is untouched. */
  private void performImport(String name, Keymap parent, List<ImportEntry> entries) {
    Keymap child = parent.deriveKeymap(name);
    for (ImportEntry e : entries) {
      // getShortcuts returns a copy, so removing while iterating is safe (same pattern as removeShortcutFrom).
      for (Shortcut existing : child.getShortcuts(e.actionId())) child.removeShortcut(e.actionId(), existing);
      for (Shortcut sc : e.shortcuts()) child.addShortcut(e.actionId(), sc);
    }
    KeymapManagerEx.getInstanceEx().getSchemeManager().addScheme(child);
    keymap = child;   // select and preview the import; use Activate to switch to it
    reloadKeymaps();
    refresh();
    updateActionButtons();
  }

  /** True if {@code name} is a usable keymap name here — non-blank, not reserved, not already taken. */
  private boolean isKeymapNameAvailable(String name) {
    if (name.isBlank() || isReservedKeymapName(name)) return false;
    return findKeymapByName(name) == null;
  }

  /** Resolve a keymap by name via {@link KeymapManager#getKeymap}, which sees every keymap — including
   *  internal roots like {@code $default} that {@link KeymapManagerEx#getAllKeymaps} hides. */
  private @Nullable Keymap findKeymapByName(@Nullable String name) {
    if (name == null || name.isBlank()) return null;
    return KeymapManagerEx.getInstanceEx().getKeymap(name);
  }

  /** Resolves the imported keymap's name and parent before it is created — prefilled from the file,
   *  editable when the name collides or the declared parent isn't installed. */
  private final class ImportDialog extends DialogWrapper {
    private static final String SELECT_PARENT = "— Select a parent —";
    private final ParsedImport parsed;
    private final JBTextField nameField = new JBTextField();
    private final ComboBox<Keymap> parentCombo = new ComboBox<>();

    ImportDialog(ParsedImport parsed) {
      super(project);
      this.parsed = parsed;
      setTitle("Import Keymap");
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      nameField.setText(suggestName(parsed.name()));
      nameField.setColumns(28);

      // Resolve the file's declared parent. If it exists it is preselected; if it isn't installed (or
      // none is declared) the combo opens on a "— Select a parent —" placeholder and doValidate() asks
      // for one in the status line, blocking OK until the user picks.
      Keymap resolved = findKeymapByName(parsed.parentName());

      List<Keymap> installed = new ArrayList<>(List.of(KeymapManagerEx.getInstanceEx().getAllKeymaps()));
      // A resolved parent may be an internal root ($default) that getAllKeymaps() hides — make it pickable.
      if (resolved != null && installed.stream().noneMatch(k -> k.getName().equals(resolved.getName()))) {
        installed.add(resolved);
      }
      installed.sort(Comparator.comparing(Keymap::getPresentableName, String.CASE_INSENSITIVE_ORDER));

      List<Keymap> items = new ArrayList<>();
      items.add(null);  // the placeholder — selected (and required to be replaced) when parent is unresolved
      items.addAll(installed);
      parentCombo.setModel(new DefaultComboBoxModel<>(items.toArray(new Keymap[0])));
      parentCombo.setRenderer(SimpleListCellRenderer.create(SELECT_PARENT,
        k -> k == null ? SELECT_PARENT : k.getPresentableName() + " " + sourceTag(k)));
      parentCombo.setSelectedItem(resolved);  // null → the placeholder

      FormBuilder form = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", nameField)
        .addLabeledComponent("Parent:", parentCombo)
        .addComponent(new JBLabel(parsed.entries().size() + " action(s), "
          + parsed.shortcutCount() + " shortcut(s)", UIUtil.ComponentStyle.SMALL));

      if (!parsed.warnings().isEmpty()) form.addComponent(warningBlock(parsed.warnings()));

      JPanel panel = form.getPanel();
      panel.setBorder(JBUI.Borders.empty(10, 12));
      return panel;
    }

    private String suggestName(String base) {
      if (isKeymapNameAvailable(base)) return base;
      for (int n = 2; ; n++) {
        String candidate = base + " (" + n + ")";
        if (isKeymapNameAvailable(candidate)) return candidate;
      }
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
      String n = chosenName();
      if (n.isEmpty()) return new ValidationInfo("Enter a name for the keymap.", nameField);
      if (isReservedKeymapName(n)) {
        return new ValidationInfo("“" + n + "” is a reserved keymap name — choose a different one.", nameField);
      }
      if (findKeymapByName(n) != null) {
        return new ValidationInfo("A keymap named “" + n + "” already exists.", nameField);
      }
      if (chosenParent() == null) return new ValidationInfo(parentPrompt(), parentCombo);
      return null;
    }

    /** The status-line message when no parent is selected — naming the file's missing parent if it had one. */
    private String parentPrompt() {
      return parsed.parentName() != null && !parsed.parentName().isBlank()
        ? "The file's parent “" + parsed.parentName() + "” isn't installed — select a parent."
        : "This file declares no parent — select one.";
    }

    String chosenName() { return nameField.getText().trim(); }
    Keymap chosenParent() { return (Keymap) parentCombo.getSelectedItem(); }
  }

  /** The parse warnings as a compact, scrollable, read-only block (capped so a bad file can't grow
   *  the dialog without bound). */
  private static JComponent warningBlock(List<String> warnings) {
    StringBuilder sb = new StringBuilder("<html><b>Notes</b><ul style='margin:2px 0 0 16px;padding:0'>");
    int shown = Math.min(warnings.size(), 12);
    for (int i = 0; i < shown; i++) sb.append("<li>").append(escape(warnings.get(i))).append("</li>");
    if (warnings.size() > shown) sb.append("<li>… and ").append(warnings.size() - shown).append(" more</li>");
    sb.append("</ul></html>");
    JBLabel label = new JBLabel(sb.toString(), UIUtil.ComponentStyle.SMALL);
    label.setForeground(UIUtil.getContextHelpForeground());
    return label;
  }

  // ---- detail pane ----------------------------------------------------------------------------

  private void showDetail(@Nullable Object payload) {
    currentPayload = payload;
    detailPanel.removeAll();
    if (payload instanceof Section s) {
      buildSectionDetail(s.title());
    }
    else if (payload instanceof Subtitle st) {
      buildSectionDetail(st.sectionTitle());   // the subtitle row explains its section
    }
    else if (payload instanceof Empty e) {
      buildEmptyDetail(e);
    }
    else if (payload instanceof KeymapItem ki) {
      buildConflictDetail(ki.c(), null);
    }
    else if (payload instanceof IdeaIgnoredItem ii) {
      buildConflictDetail(ii.c(),
        "{ide}'s own Keymap settings does <b>not</b> flag this as a conflict — it deliberately "
        + "ignores window-switching overlaps. It is listed here only for completeness; the shortcut "
        + "works. Changing it is an optional, purely cosmetic tidy-up.");
    }
    else if (payload instanceof ConflictAdvice.Supplement s) {
      buildSupplementDetail(s);
    }
    else if (payload instanceof ConflictScan.InternalConflict c) {
      buildInternalDetail(c);
    }
    else if (payload instanceof ModifiedItem mi) {
      buildModifiedDetail(mi.m());
    }
    else if (payload instanceof InheritedItem it) {
      buildInheritedDetail(it.m());
    }
    detailPanel.revalidate();
    detailPanel.repaint();
    SwingUtilities.invokeLater(() -> detailScroll.getVerticalScrollBar().setValue(0));
  }

  /** A category's detail: heading + full blurb, then the category-level links.
   *  <b>Inherited Shortcuts</b> gets only <b>Settings…</b>; every other category also gets bulk
   *  <b>Remove…</b> over all its actions and <b>Revert…</b> over its {@link #revertable} ones
   *  (both deselectable in the confirm dialog). */
  private void buildSectionDetail(String title) {
    addHtml("<h3 style='margin:0 0 4px 0'>" + escape(title) + "</h3>"
      + grayBlock(escape(sectionBlurb(title))));
    addBlock(categoryLinks(title));
  }

  /**
   * The details for an empty category's placeholder row: the category heading, its one-line status —
   * green ✔ for "nothing to do", amber ⚠ when the scan itself couldn't run — then what would be listed
   * here and why nothing is (see {@link #emptyExplanation}), then the category links, which for an empty
   * category reduce to <b>Settings…</b> on their own.
   */
  private void buildEmptyDetail(Empty e) {
    String colour = hex(e.ok() ? okColour() : warnColour());
    addHtml("<h3 style='margin:0 0 4px 0'>" + escape(e.sectionTitle()) + "</h3>"
      + "<div style='margin:0 0 8px 0; font-weight:bold; color:" + colour + "'>"
      + (e.ok() ? "&#10004;" : "&#9888;") + "&nbsp; " + escape(e.message()) + "</div>"
      + grayBlock(escape(e.explanation())));
    addBlock(categoryLinks(e.sectionTitle()));
  }

  /** A category's own links: bulk <b>Remove…</b> over all its actions and <b>Revert…</b> over its
   *  {@link #revertable} ones, then <b>Settings…</b>. Inherited Shortcuts gets Settings only, and so does
   *  any category with nothing in it. */
  private JComponent categoryLinks(String title) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
    row.setOpaque(false);
    if (!title.equals(SEC_INHERITED)) {
      List<String> ids = categoryIds(title);
      if (!ids.isEmpty()) {
        row.add(new ActionLink("Remove…", (ActionListener) e -> confirmRemove(null, ids)));
        row.add(dot());
        // Only the own-declared ones: an inherited binding already equals the parent's, so listing it
        // in the confirm dialog would show "current → identical" and do nothing.
        List<String> revertable = revertableIds(ids);
        if (!revertable.isEmpty()) {
          row.add(new ActionLink("Revert…", (ActionListener) e -> confirmRevert(revertable)));
          row.add(dot());
        }
      }
    }
    row.add(settingsLink());
    return row;
  }

  /** All action ids in a category (used by its bulk Remove/Revert links). */
  private List<String> categoryIds(String title) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    if (title.equals(SEC_KEYMAP) || title.equals(SEC_IDEA_IGNORED)) {
      boolean ignored = title.equals(SEC_IDEA_IGNORED);
      for (ConflictScan.ExternalConflict c : scan.keymapConflicts) {
        if (ideaIgnored(c) == ignored) for (ConflictScan.ActionRef a : c.actions()) ids.add(a.id());
      }
    }
    else if (title.equals(SEC_DOUBLE)) {
      for (ConflictScan.InternalConflict c : scan.internal) for (ConflictScan.ActionRef a : c.actions()) ids.add(a.id());
    }
    else if (title.equals(SEC_MODIFIED)) {
      for (ConflictScan.ModifiedBinding m : scan.modified) ids.add(m.action().id());
    }
    else if (title.equals(SEC_INHERITED)) {
      for (ConflictScan.ModifiedBinding m : scan.inherited) ids.add(m.action().id());
    }
    return new ArrayList<>(ids);
  }

  /** The label for a set of actions: the first action's name plus a "+N" count when there are more. */
  private static String actionsLabel(List<ConflictScan.ActionRef> a) {
    if (a.isEmpty()) return "";
    return a.get(0).label() + (a.size() > 1 ? "  +" + (a.size() - 1) : "");
  }

  /** The dimmed meta for a conflict/double-bound navigator row: the first action's id/keymap meta when a
   *  gear toggle is on (matching the first-action name in {@link #actionsLabel}); otherwise the notable
   *  binding source. Per-action detail (the Actions list) still shows the meta for every action. */
  private @Nullable String conflictMeta(List<ConflictScan.ActionRef> a) {
    if (!a.isEmpty()) {
      String meta = actionMeta(a.get(0).id());
      if (meta != null) return meta;
    }
    return notableSources(a);
  }

  /** A conflict on one keystroke: facts and status on top, the interactive action list and its fix
   *  links in the middle, the advice below. */
  private void buildConflictDetail(ConflictScan.ExternalConflict c, @Nullable String extraNoteHtml) {
    addBlock(shortcutHeaderRow(Keycaps.forKeystroke(c.stroke(), null), null));
    addHtml(statusBadge(c) + factRow("macOS", escape(macList(c))));
    addBlock(boldLabel("Actions"));
    ActionListView list = new ActionListView(c.stroke(), c.actions());
    detailPanel.add(list);
    addBlock(linksRow(c.stroke(), c.actions(), list));
    String bottom = callout("What to do", escape(c.advice().note()));
    if (extraNoteHtml != null) bottom += callout("Not flagged by {ide}", extraNoteHtml);
    addHtml(bottom);
  }

  /** A key bound to several actions in the keymap; interactive so a stacked one can be moved off. */
  private void buildInternalDetail(ConflictScan.InternalConflict c) {
    KeyStroke first = ((KeyboardShortcut) c.shortcut()).getFirstKeyStroke();
    addBlock(shortcutHeaderRow(Keycaps.forShortcut(c.shortcut()), null));
    addHtml(internalStatus(c));
    addBlock(boldLabel("Actions"));
    ActionListView list = new ActionListView(first, c.actions());
    detailPanel.add(list);
    addBlock(linksRow(first, c.actions(), list));
    addHtml(callout("What to do", escape(internalNote(c))));
  }

  /** A shortcut this keymap declares itself (differs from the parent): its action, binding and source,
   *  and the full edit links — Rebind… · Remove… · Revert… · Settings…. Revert drops the own
   *  declaration so the parent binding re-inherits; read-only keymaps derive an editable copy first. */
  private void buildModifiedDetail(ConflictScan.ModifiedBinding m) {
    ConflictScan.ActionRef a = m.action();
    boolean cleared = m.shortcuts().isEmpty();
    String header = cleared ? "— (inherited binding cleared)" : shortcutsText(m.shortcuts());
    String body = cleared
      ? "This keymap removes a shortcut it would otherwise inherit from the parent keymap."
      : "This keymap declares this shortcut itself, so it differs from the parent keymap it inherits from.";
    addBlock(shortcutHeaderRow(cleared ? null : Keycaps.forShortcuts(m.shortcuts(), null), header));
    addHtml(factRow("Action", actionLabelHtml(a))
      + (a.source() != null ? factRow("Source", escape(a.source())) : "")
      + callout("What this is", escape(body)));
    addBlock(actionEditLinks(a, m.shortcuts()));
  }

  /** A shortcut inherited from a parent keymap: the same info as a modified row, editable here
   *  (Rebind/Remove derive a copy). No Revert link — an inherited binding already equals the parent,
   *  which {@link #revertable} works out on its own. */
  private void buildInheritedDetail(ConflictScan.ModifiedBinding m) {
    ConflictScan.ActionRef a = m.action();
    addBlock(shortcutHeaderRow(Keycaps.forShortcuts(m.shortcuts(), null), null));
    addHtml(factRow("Action", actionLabelHtml(a))
      + (a.source() != null ? factRow("Source", escape(a.source())) : "")
      + callout("What this is", escape("This keymap inherits this shortcut from a parent keymap. Rebind "
        + "or remove it here to override the inherited binding; the first edit derives an editable copy.")));
    addBlock(actionEditLinks(a, m.shortcuts()));
  }

  /** A curated overlap the live scan can't see (Emoji & Symbols, Dictation). The macOS side isn't in the
   *  scanned table, but the IDE action bound to the key <b>is</b> in the keymap, so we offer the same edit
   *  links on it (Rebind when it's a keystroke; Remove clears it; Settings). Falls back to Settings only
   *  when the action isn't bound here. */
  private void buildSupplementDetail(ConflictAdvice.Supplement s) {
    List<Shortcut> scs = s.actionId() != null ? List.of(keymap.getShortcuts(s.actionId())) : List.of();
    String tail = scs.isEmpty()
      ? " The macOS side can't be detected by the live scan, and no IDE action is bound to this key here."
      : " The macOS side can't be detected by the live scan; the IDE action bound to this key is editable below.";
    addBlock(shortcutHeaderRow(null, s.keys()));   // "keys" is descriptive text (may be a gesture), not a Shortcut
    addHtml(factRow("{ide}", escape(s.ideaSide()))
      + factRow("macOS", escape(s.macSide()))
      + callout("What this means", escape(s.note()) + tail));
    if (!scs.isEmpty()) {
      addBlock(actionEditLinks(new ConflictScan.ActionRef(s.actionId(), null, null, null), scs));
    }
    else {
      JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
      row.setOpaque(false);
      // The action isn't bound here, but it still exists — land on it so a shortcut can be assigned.
      row.add(settingsLink(s.actionId()));
      addBlock(row);
    }
  }

  /** The per-row edit links for one action's binding: Rebind… · Remove… · [Revert…] · Settings….
   *  Rebind moves the action's primary keystroke (omitted when it has only a mouse/gesture binding);
   *  Remove clears <b>all</b> its shortcuts; Revert appears exactly when the action is
   *  {@link #revertable} — which is what keeps it off the Inherited rows without a special case. */
  private JComponent actionEditLinks(ConflictScan.ActionRef a, List<Shortcut> shortcuts) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
    row.setOpaque(false);
    KeyStroke stroke = primaryStroke(shortcuts);
    if (stroke != null) {
      row.add(new ActionLink("Rebind…", (ActionListener) e -> rebindActions(stroke, List.of(a.id()))));
      row.add(dot());
    }
    if (!shortcuts.isEmpty()) {
      row.add(new ActionLink("Remove…", (ActionListener) e -> confirmRemove(null, List.of(a.id()))));
      row.add(dot());
    }
    if (revertable(a.id())) {
      row.add(new ActionLink("Revert…", (ActionListener) e -> confirmRevert(List.of(a.id()))));
      row.add(dot());
    }
    row.add(settingsLink(a.id()));
    return row;
  }

  /** The first keyboard keystroke among an action's shortcuts, or null if it has none (mouse/gesture only). */
  private static @Nullable KeyStroke primaryStroke(List<Shortcut> shortcuts) {
    for (Shortcut sc : shortcuts) {
      if (sc instanceof KeyboardShortcut ks) return ks.getFirstKeyStroke();
    }
    return null;
  }

  /** A "Settings…" link that opens the platform Keymap settings, with a trailing external-link arrow
   *  signalling it leaves the plugin (spec 0003). */
  private ActionLink settingsLink() {
    return settingsLink(null);
  }

  /** The same link, but landing on one action: the Keymap page opens with {@code actionId} revealed and
   *  selected in its tree, so the user doesn't have to search for the row they just came from. The
   *  tooltip names it, since the link text can't. */
  private ActionLink settingsLink(@Nullable String actionId) {
    ActionLink link = new ActionLink("Settings…", (ActionListener) e -> openKeymapSettings(actionId));
    link.setExternalLinkIcon();
    if (actionId != null) link.setToolTipText("Open Keymap settings at “" + actionName(actionId) + "”");
    return link;
  }

  /** Action name as HTML, with the "Show Action IDs" / "Show Keymap" meta appended in the dimmed id
   *  colour when either toggle is on. */
  private String actionLabelHtml(ConflictScan.ActionRef a) {
    String s = escape(a.label());
    String meta = actionMeta(a.id());
    if (meta != null) s += "&nbsp;&nbsp;<span style='color:" + hex(grayColour()) + "'>" + escape(meta) + "</span>";
    return s;
  }

  /** The meta shown next to an action name per the gear toggles. Format (spec 0003): the action
   *  <b>id</b> in parentheses, the defining <b>keymap</b> in brackets, joined by a dash —
   *  {@code (id) - [Keymap]}, {@code (id)}, or {@code [Keymap]}. Null when neither toggle applies. */
  private @Nullable String actionMeta(String id) {
    String km = showKeymap ? definingKeymapName(id) : null;
    String aid = showActionIds ? id : null;
    if (aid != null) return km != null ? "(" + aid + ") - [" + km + "]" : "(" + aid + ")";
    return km != null ? "[" + km + "]" : null;
  }

  /** The keymap that defines this action's current binding: the nearest keymap up the parent chain
   *  (from the selected one) that <b>declares</b> the id in its own scheme; failing that (e.g. a binding
   *  contributed by a plugin's default keymap, present in no scheme), the topmost keymap in the chain
   *  that still provides the binding. Null only when nothing in the chain binds it. */
  private @Nullable String definingKeymapName(String id) {
    for (Keymap k = keymap; k != null; k = k.getParent()) {
      if (ownIds(k).contains(id)) return k.getPresentableName();
    }
    Keymap base = null;   // fallback: the base keymap that supplies a plugin/built-in default
    for (Keymap k = keymap; k != null; k = k.getParent()) {
      if (k.getShortcuts(id).length > 0) base = k;
    }
    return base != null ? base.getPresentableName() : null;
  }

  /**
   * Whether reverting this action could change anything. Only a binding the <b>viewed</b> keymap
   * <b>declares itself</b> is revertable: for anything else the effective binding already <em>is</em> the
   * parent's, so {@link #revertToDefault} would be a no-op ({@code clearOwnActionsId} has nothing to
   * drop; the read-only path would re-add the identical binding).
   * <p>That own-declaration set is exactly what the "Modified shortcuts" section lists — which is why
   * Revert was long offered only there. But a conflict / double-bound / overlap / supplement row names
   * actions too, and those are frequently own-declared (the whole point of a customized keymap), so the
   * link belongs on those rows as well — gated on this predicate so it never appears inert.
   */
  private boolean revertable(String id) {
    return ownIds(keymap).contains(id);
  }

  /** The revertable subset of a list of action ids, order-preserving and de-duplicated. */
  private List<String> revertableIds(java.util.Collection<String> ids) {
    return ids.stream().distinct().filter(this::revertable).toList();
  }

  /** A keymap's own declared action ids (from {@link KeymapImpl#writeScheme}), cached; the cache is
   *  cleared on {@link #refresh()} since an edit changes the selected keymap's declarations. */
  private Set<String> ownIds(Keymap k) {
    return ownIdsCache.computeIfAbsent(k, kk -> {
      Set<String> ids = new HashSet<>();
      if (kk instanceof KeymapImpl impl) {
        for (Object child : impl.writeScheme().getChildren("action")) {
          String id = ((Element) child).getAttributeValue("id");
          if (id != null && !id.isEmpty()) ids.add(id);
        }
      }
      return ids;
    });
  }

  /** Comma-joined rendering of a set of shortcuts (empty string when there are none). */
  private static String shortcutsText(List<Shortcut> shortcuts) {
    return String.join(", ", shortcuts.stream().map(KeymapUtil::getShortcutText).toList());
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
   *  if none are checked); "Suggest…" does the same but opens the rebind dialog with a
   *  {@link ShortcutSuggester} pick already filled in, still awaiting the user's OK; "Remove…" clears
   *  the shortcut after a confirmation; "Revert…" re-inherits the parent binding — shown only when at
   *  least one action on this key is {@link #revertable} (i.e. the keymap declares it itself), and
   *  acting on the checked ones among those, or on all of them when the checkboxes name none. */
  private JComponent linksRow(KeyStroke stroke, List<ConflictScan.ActionRef> actions, ActionListView list) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
    row.setOpaque(false);
    List<String> allIds = actions.stream().map(ConflictScan.ActionRef::id).toList();
    row.add(new ActionLink("Rebind…", (ActionListener) e -> {
      List<String> checked = list.checkedIds();
      rebindActions(stroke, checked.isEmpty() ? allIds : checked);
    }));
    row.add(dot());
    row.add(new ActionLink("Suggest…", (ActionListener) e -> {
      List<String> checked = list.checkedIds();
      suggestAndRebind(stroke, checked.isEmpty() ? allIds : checked);
    }));
    row.add(dot());
    row.add(new ActionLink("Remove…", (ActionListener) e -> {
      List<String> checked = list.checkedIds();
      confirmRemove(stroke, checked.isEmpty() ? allIds : checked);
    }));
    row.add(dot());
    List<String> revertable = revertableIds(allIds);
    if (!revertable.isEmpty()) {
      row.add(new ActionLink("Revert…", (ActionListener) e -> {
        List<String> checked = revertableIds(list.checkedIds());
        confirmRevert(checked.isEmpty() ? revertable : checked);
      }));
      row.add(dot());
    }
    // Settings… can only land on one action, so it takes the key's first — the same one the navigator
    // row's label and meta name (see actionsLabel / conflictMeta), so the tooltip can't surprise anyone.
    row.add(allIds.isEmpty() ? settingsLink() : settingsLink(allIds.get(0)));
    return row;
  }

  private JComponent dot() {
    JBLabel d = new JBLabel("•");
    d.setForeground(grayColour());
    return d;
  }

  /** Confirm removal, then clear exactly the shortcuts the user left ticked. {@code stroke != null}
   *  (reached from a conflict) pre-ticks only the shortcuts on that key, so the default scope is the same
   *  keystroke-only removal as before; {@code stroke == null} pre-ticks every shortcut of each action. */
  private void confirmRemove(@Nullable KeyStroke stroke, List<String> ids) {
    if (ids.isEmpty()) return;
    RemoveConfirmDialog dialog = new RemoveConfirmDialog(stroke, ids);
    if (dialog.showAndGet()) removeShortcuts(dialog.pickedShortcuts());
  }

  /** Remove confirmation. Every shortcut of every listed action is individually tickable; reached from a
   *  conflict (a "Current:" keycap header) only the shortcuts on that key start ticked, so the default
   *  scope is unchanged. The list is shown by default and scrollable, with a "Hide actions" checkbox. */
  private final class RemoveConfirmDialog extends DialogWrapper {
    private final @Nullable KeyStroke stroke;
    private final List<String> ids;
    private EditActionList list;
    private JComponent listScroll;
    private @Nullable ActionLink selectAll;   // only when there is more than one action to pick from

    RemoveConfirmDialog(@Nullable KeyStroke stroke, List<String> ids) {
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
      // From a conflict, only that key's shortcuts start ticked — the scope this dialog always had, now
      // visible and adjustable rather than implied.
      list = new EditActionList(ids, false, this, sc -> stroke == null || onStroke(sc, stroke));
      listScroll = cappedScroll(list);

      JBLabel summary = new JBLabel();
      summary.setAlignmentX(Component.LEFT_ALIGNMENT);
      list.setOnChange(() -> summary.setText(removeSummary()));
      summary.setText(removeSummary());

      FormBuilder form = FormBuilder.createFormBuilder();
      if (stroke != null) form.addLabeledComponent("Current:", Keycaps.forKeystroke(stroke, null));
      form.addComponent(summary);
      if (ids.size() > 1) {   // a one-row list has nothing to select all of
        selectAll = list.selectAllLink();
        form.addComponent(selectAll);
      }
      return listLayout(form.getPanel(), listScroll);
    }

    /** Counts shortcuts, not actions — that is what gets removed now. */
    private String removeSummary() {
      int n = list.pickedShortcutCount();
      int actions = list.pickedShortcuts().size();
      return "Remove " + n + " of " + list.totalShortcutCount() + " shortcut" + (n == 1 ? "" : "s")
        + " from " + actions + " action" + (actions == 1 ? "" : "s") + "?";
    }

    @Override
    protected JComponent createDoNotAskCheckbox() {
      JCheckBox hide = new JCheckBox("Hide actions");   // list shown by default; this collapses it
      hide.addActionListener(e -> {
        boolean show = !hide.isSelected();
        listScroll.setVisible(show);
        if (selectAll != null) selectAll.setVisible(show);   // no stray toggle over a collapsed list
        resizeToFit(listScroll);
      });
      return hide;
    }

    Map<String, List<Shortcut>> pickedShortcuts() {
      return list.pickedShortcuts();
    }
  }

  /** True when a shortcut is the keyboard shortcut whose first keystroke is {@code stroke} — the test
   *  that scopes "this key" in the Remove and Rebind dialogs. */
  private static boolean onStroke(Shortcut sc, KeyStroke stroke) {
    return sc instanceof KeyboardShortcut ks && stroke.equals(ks.getFirstKeyStroke());
  }

  /** Confirm, then revert. A row whose shortcuts are <b>all</b> ticked reverts wholly (re-inherits from
   *  the parent); one with only some ticked reverts partially — see {@link #revertToDefault}. */
  private void confirmRevert(List<String> ids) {
    if (ids.isEmpty()) return;   // nothing revertable in the selection (matches confirmRemove)
    RevertConfirmDialog dialog = new RevertConfirmDialog(ids);
    if (dialog.showAndGet()) revertToDefault(dialog.selectedIds(), dialog.keptShortcuts());
  }

  /** Revert the given actions to the binding they would inherit from the <b>viewed</b> keymap's parent.
   *  <p>Two paths, because {@code deriveKeymap} of a read-only keymap yields a copy whose parent is that
   *  keymap (with no own declarations), so {@code clearOwnActionsId} on it would be a no-op:
   *  <ul>
   *    <li><b>Editable</b> keymap → {@link KeymapImpl#clearOwnActionsId} drops the own declaration so the
   *        parent binding re-inherits.</li>
   *    <li><b>Read-only</b> keymap → after {@link #ensureEditable()} derives a copy (parent = the
   *        read-only keymap), explicitly <em>replace</em> the copy's binding with the read-only keymap's
   *        <em>own parent's</em> binding (clear-then-add) — the same effective result the editable path
   *        gives.</li>
   *  </ul>
   *  Either way an action the parent doesn't bind becomes unbound.
   *  <p><b>Partial revert.</b> {@code kept} names shortcuts the user left <em>unticked</em> in the confirm
   *  dialog, i.e. ones to hold on to. Reverting is a whole-action operation — the parent supplies a
   *  <em>set</em>, so a single shortcut can't be reverted on its own — and the faithful reading of
   *  "the operation only includes the ticked shortcuts" is: the action ends up with the parent's binding
   *  <em>plus</em> the kept ones. An action with nothing kept takes the clean path above and genuinely
   *  re-inherits; one with something kept necessarily keeps an own declaration. */
  private void revertToDefault(List<String> ids, Map<String, List<Shortcut>> kept) {
    boolean wasReadOnly = !keymap.canModify();
    Keymap viewedParent = keymap.getParent();   // capture before ensureEditable() reassigns `keymap`
    Map<String, List<Shortcut>> targets = new LinkedHashMap<>();
    for (String id : ids) {   // the parent's binding, needed by the read-only and the partial paths alike
      targets.put(id, viewedParent != null ? List.of(viewedParent.getShortcuts(id)) : List.of());
    }
    Keymap target = ensureEditable();
    if (!(target instanceof KeymapImpl impl)) return;
    for (String id : ids) {
      List<Shortcut> keep = kept.getOrDefault(id, List.of());
      if (wasReadOnly || !keep.isEmpty()) {
        // Replace the binding outright: the parent's set, plus whatever the user chose to keep.
        for (Shortcut sc : target.getShortcuts(id)) target.removeShortcut(id, sc);
        for (Shortcut sc : targets.getOrDefault(id, List.of())) target.addShortcut(id, sc);
        for (Shortcut sc : keep) {
          if (!List.of(target.getShortcuts(id)).contains(sc)) target.addShortcut(id, sc);
        }
      }
      else {
        impl.clearOwnActionsId(id);   // drop own declaration → re-inherit parent
      }
    }
    // clearOwnActionsId / this replace don't reliably fire the shortcut-changed event, so publish it
    // ourselves — the same public notification the platform uses — so the live IDE refreshes too.
    ApplicationManager.getApplication().getMessageBus()
      .syncPublisher(KeymapManagerListener.TOPIC).shortcutsChanged(impl, ids, false);
    refresh();
    updateActionButtons();
  }

  /** The binding an action reverts to: what the currently-viewed keymap's parent provides for it (empty
   *  = the revert leaves it unbound). Shown per-row in the Revert dialog; matches {@link #revertToDefault}. */
  private List<Shortcut> revertTarget(String id) {
    Keymap parent = keymap.getParent();
    return parent != null ? List.of(parent.getShortcuts(id)) : List.of();
  }

  /**
   * The center-panel layout the Rebind / Remove / Revert dialogs share: the fixed form rows pinned
   * {@code NORTH}, the action list {@code CENTER}.
   * <p>All three used to put the list <i>inside</i> the NORTH block, and that is what made a long list
   * unreachable: {@code BorderLayout.NORTH} always lays a component out at its <b>preferred</b> height
   * and never shrinks it, so as soon as the window was shorter than the whole form — clamped to the
   * screen on open, or dragged smaller — the bottom was simply cut off, outside the scroll pane and
   * therefore with nothing to scroll. Rebind hit it first because it stacks six more rows above the list
   * (Current / Key / Modifiers / New / status / moves-count) than the other two.
   * <p>{@code CENTER} takes the leftover height instead, so the list gives up space first and scrolls
   * within it. At the window's preferred size nothing looks different — CENTER's preferred height is the
   * capped one from {@link #cappedScroll}.
   */
  private static JPanel listLayout(JComponent fixedRows, JComponent listScroll) {
    JPanel wrap = new JPanel(new BorderLayout(0, JBUI.scale(6)));   // vgap = the form's own row spacing
    wrap.add(fixedRows, BorderLayout.NORTH);
    wrap.add(listScroll, BorderLayout.CENTER);
    return wrap;
  }

  /** A scroll pane around a dialog's action list, capped at ~480px tall so a long list can't grow the
   *  window without bound (spec 0003); it scrolls instead. Sized to the content up to that cap. */
  private static JComponent cappedScroll(JComponent content) {
    JBScrollPane sp = new JBScrollPane(content);
    sp.setBorder(JBUI.Borders.empty());
    sp.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    int max = JBUI.scale(480);
    int h = Math.min(content.getPreferredSize().height + JBUI.scale(4), max);
    sp.setPreferredSize(new Dimension(content.getPreferredSize().width + JBUI.scale(20), h));
    sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, max));
    sp.setAlignmentX(Component.LEFT_ALIGNMENT);
    return sp;
  }

  /** Revert confirmation. A single-action revert shows a "Current:" / "Reverts to:" keycap header; a
   *  bulk revert shows a per-row {@code current → reverts-to} pick list (each action can have a different
   *  current binding and target), expanded by default and scrollable, with a "Hide actions" toggle. A
   *  read-only keymap is copied to an editable one on confirm, so it says so. */
  private final class RevertConfirmDialog extends DialogWrapper {
    private final List<String> ids;
    private final boolean bulk;
    private EditActionList revertList;   // bulk only
    private JComponent listScroll;       // bulk only
    private ActionLink selectAll;        // bulk only

    RevertConfirmDialog(List<String> ids) {
      super(project);
      this.ids = ids;
      this.bulk = ids.size() > 1;
      setTitle("Revert to Default");
      init();
      setResizable(true);  // a resizable window is movable on macOS and can be resized to fit content
      setOKButtonText("Revert");
    }

    @Override
    protected JComponent createCenterPanel() {
      JBLabel summary = new JBLabel();
      summary.setAlignmentX(Component.LEFT_ALIGNMENT);

      FormBuilder form = FormBuilder.createFormBuilder();
      if (!bulk) {   // one action: show its current binding and what it reverts to, in the header
        String id = ids.get(0);
        List<Shortcut> current = List.of(keymap.getShortcuts(id));
        List<Shortcut> to = revertTarget(id);
        form.addLabeledComponent("Current:", current.isEmpty()
          ? grayText("—") : Keycaps.forShortcuts(current, null));
        form.addLabeledComponent("Reverts to:", to.isEmpty()
          ? grayText("unbound") : Keycaps.forShortcuts(to, null));
      }
      form.addComponent(summary);
      if (!keymap.canModify()) {   // reverting derives+activates an editable copy, like Rebind/Remove
        JBLabel copyNote = grayText("“" + keymap.getPresentableName()
          + "” is read-only — an editable copy will be created and activated.");
        copyNote.setBorder(JBUI.Borders.emptyTop(6));
        form.addComponent(copyNote);
      }
      if (bulk) {
        revertList = new EditActionList(ids, true, this);
        revertList.setOnChange(() -> summary.setText(revertSummary()));
        listScroll = cappedScroll(revertList);
        selectAll = revertList.selectAllLink();
        form.addComponent(selectAll);
      }
      summary.setText(revertSummary());

      if (!bulk) {   // no list at all — the header rows are the whole dialog
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(form.getPanel(), BorderLayout.NORTH);
        return wrap;
      }
      return listLayout(form.getPanel(), listScroll);
    }

    private String revertSummary() {
      if (!bulk) return "Revert this shortcut to the parent keymap?";
      int actions = revertList.checkedIds().size();
      int partial = actions - revertList.fullyPickedIds().size();
      return "Revert " + actions + " of " + ids.size() + " action" + (actions == 1 ? "" : "s")
        + " to the parent keymap?"
        + (partial > 0 ? "  " + partial + " keep" + (partial == 1 ? "s" : "") + " an unticked shortcut." : "");
    }

    @Override
    protected JComponent createDoNotAskCheckbox() {
      if (!bulk) return null;   // nothing to hide for a single action
      JCheckBox hide = new JCheckBox("Hide actions");
      hide.addActionListener(e -> {
        boolean show = !hide.isSelected();
        listScroll.setVisible(show);
        selectAll.setVisible(show);   // no stray toggle over a collapsed list
        resizeToFit(listScroll);
      });
      return hide;
    }

    List<String> selectedIds() {
      return bulk ? revertList.checkedIds() : ids;
    }

    /** Per action, the shortcuts left <b>unticked</b> — the ones to hold on to across the revert. Empty
     *  for a single-action revert, which has no list and so reverts wholly. */
    Map<String, List<Shortcut>> keptShortcuts() {
      if (!bulk) return Map.of();
      Map<String, List<Shortcut>> kept = new LinkedHashMap<>();
      Map<String, List<Shortcut>> picked = revertList.pickedShortcuts();
      for (String id : revertList.checkedIds()) {
        List<Shortcut> on = picked.getOrDefault(id, List.of());
        List<Shortcut> off = new ArrayList<>();
        for (Shortcut sc : keymap.getShortcuts(id)) if (!on.contains(sc)) off.add(sc);
        if (!off.isEmpty()) kept.put(id, off);
      }
      return kept;
    }
  }

  /** A gray context-help label — used for the "—" / "unbound" / read-only notes in the revert dialog. */
  private JBLabel grayText(String text) {
    JBLabel l = new JBLabel(text);
    l.setForeground(grayColour());
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
  }

  /**
   * Let a text component give up width in a {@link BoxLayout#X_AXIS} row. Without this a row that is
   * naturally wider than the pane keeps its full width, the trailing glue collapses, and the keycaps
   * land wherever the text ends — ragged, and past the right edge. With a zero minimum, BoxLayout
   * compresses the text (a JLabel / JCheckBox paints "…" when clipped) and the keycaps stay flush right.
   */
  private static <T extends JComponent> T shrinkable(T c) {
    c.setMinimumSize(new Dimension(0, c.getPreferredSize().height));
    return c;
  }

  /** Pin a component at its preferred size, so row compression is taken out of the text next to it and
   *  never out of the keycaps themselves. */
  private static <T extends JComponent> T rigid(T c) {
    Dimension d = c.getPreferredSize();
    c.setMinimumSize(d);
    c.setMaximumSize(d);
    return c;
  }

  /**
   * The editable pick list shared by the Rebind / Remove / Revert dialogs. One row per action: a
   * checkbox carrying the action's name, its dimmed {@code (id) - [keymap]} meta, and <b>each of its
   * current shortcuts as its own toggle</b> — so an operation can be narrowed to individual shortcuts of
   * an action, not just to whole actions. For Revert the row also shows {@code → [reverts-to]} (the
   * parent binding via {@link #revertTarget}; "unbound" when the parent binds nothing).
   * <p>Row checkbox and shortcut toggles are kept consistent, per the agreed rules: selecting a row turns
   * every one of its shortcuts on, clearing it turns them all off, and turning the <em>last</em> shortcut
   * off clears the row. An action with a single shortcut therefore shows <b>no</b> separate toggle — under
   * those rules the row checkbox and that one toggle can never disagree, so a second control would be
   * pure noise. An action with no shortcut at all (a cleared inherited binding, which Revert can restore)
   * shows just the row checkbox.
   * <p>{@code preselect} decides which shortcuts start on: everything for a category-wide edit, only the
   * matching keystroke when the dialog was opened from one conflict — which is exactly the scope those
   * operations always had, now visible instead of implied.
   */
  private final class EditActionList extends WidthTrackingList {
    private final List<Row> rows = new ArrayList<>();
    private final DialogWrapper host;   // the dialog to dismiss when a row's settings icon navigates away
    private Runnable onChange;
    private @Nullable ActionLink selectToggle;   // created on demand by selectAllLink()
    private boolean syncing;   // guards the row <-> shortcut cascade against listener re-entry
    private boolean quiet;     // batches the change notification across a multi-row gesture

    EditActionList(java.util.Collection<String> actionIds, boolean showTarget, DialogWrapper host) {
      this(actionIds, showTarget, host, sc -> true);
    }

    EditActionList(java.util.Collection<String> actionIds, boolean showTarget, DialogWrapper host,
                   Predicate<Shortcut> preselect) {
      this.host = host;
      // layout/opaque/alignment come from WidthTrackingList; the right inset clears a scrollbar, as in
      // the navigator — same constant, so the settings icon sits the same distance from the edge.
      setBorder(JBUI.Borders.empty(4, 6, 0, ROW_RIGHT_INSET));
      for (String id : actionIds.stream().sorted().toList()) {
        Row row = new Row(id, showTarget, preselect);
        rows.add(row);
        add(row.panel);
      }
    }

    /** One action's row: the selection checkbox, plus a toggle per shortcut when it has more than one. */
    private final class Row {
      private final String id;
      private final JCheckBox box;
      private final List<Shortcut> shortcuts;
      private final List<JCheckBox> picks = new ArrayList<>();   // parallel to shortcuts; empty if ≤1
      private final JPanel panel;

      Row(String id, boolean showTarget, Predicate<Shortcut> preselect) {
        this.id = id;
        this.shortcuts = List.of(keymap.getShortcuts(id));   // all shortcuts, not just one (spec 0003)

        panel = new JPanel();   // X-axis box so everything is vertically centred and the caps right-align
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        box = new JCheckBox(actionName(id), shortcuts.stream().anyMatch(preselect) || shortcuts.isEmpty());
        box.setOpaque(false);
        box.setAlignmentY(Component.CENTER_ALIGNMENT);
        box.addItemListener(e -> rowToggled());
        panel.add(shrinkable(box));

        // Glue first, so the dimmed meta lands on the *right* — just left of the keycaps — exactly as in
        // the navigator, instead of trailing the action name on the left.
        panel.add(Box.createHorizontalGlue());
        String meta = actionMeta(id);   // dimmed keymap/id meta (see actionMeta)
        if (meta != null) {
          panel.add(shrinkable(grayText(meta)));
          if (!shortcuts.isEmpty() || showTarget) panel.add(Box.createHorizontalStrut(JBUI.scale(8)));
        }
        if (shortcuts.size() == 1) {
          panel.add(rigid(vcenter(Keycaps.forShortcut(shortcuts.get(0)))));
        }
        else {
          for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut sc = shortcuts.get(i);
            if (i > 0) panel.add(Box.createHorizontalStrut(JBUI.scale(8)));
            JCheckBox pick = new JCheckBox("", preselect.test(sc));
            pick.setOpaque(false);
            pick.setToolTipText("Include this shortcut in the operation");
            pick.addItemListener(e -> shortcutToggled());
            picks.add(pick);
            panel.add(rigid(vcenter(pick)));
            panel.add(rigid(vcenter(Keycaps.forShortcut(sc))));
          }
        }
        if (showTarget) {
          panel.add(rigid(vcenter(grayText("→"))));
          List<Shortcut> to = revertTarget(id);
          panel.add(rigid(vcenter(to.isEmpty() ? grayText("unbound") : Keycaps.forShortcuts(to, null))));
        }
        // Rightmost column, as in the navigator: the blue external-link arrow onto the platform Keymap
        // page. Here it can be a real button, so it gets hover feedback and a tooltip for free.
        panel.add(Box.createHorizontalStrut(JBUI.scale(ICON_GAP)));
        panel.add(rigid(vcenter(settingsIconButton(id))));
      }

      /** Selecting a row turns every shortcut on; clearing it turns them all off. */
      private void rowToggled() {
        if (!syncing) {
          syncing = true;
          try {
            for (JCheckBox pick : picks) pick.setSelected(box.isSelected());
          }
          finally {
            syncing = false;
          }
        }
        changed();
      }

      /** Turning the last shortcut off clears the row; turning one on re-selects it. */
      private void shortcutToggled() {
        if (!syncing) {
          syncing = true;
          try {
            box.setSelected(picks.stream().anyMatch(JCheckBox::isSelected));
          }
          finally {
            syncing = false;
          }
        }
        changed();
      }

      /** The shortcuts this row contributes to the operation: none at all when the row is cleared. */
      List<Shortcut> picked() {
        if (!box.isSelected()) return List.of();
        if (picks.isEmpty()) return shortcuts;   // 0 or 1 shortcut: the row checkbox is the toggle
        List<Shortcut> out = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) if (picks.get(i).isSelected()) out.add(shortcuts.get(i));
        return out;
      }

      /** True when every one of the action's shortcuts is included — the "whole action" case. */
      boolean allPicked() {
        return box.isSelected() && picked().size() == shortcuts.size();
      }
    }

    /** Notify once per user gesture: a cascade sets several checkboxes, and each of those fires its own
     *  listener, so the ones raised while {@link #syncing} are swallowed and the initiating handler
     *  reports after the flag clears. Without this, "Select all" over a long list would rebuild the
     *  summary once per checkbox. */
    private void changed() {
      if (syncing || quiet) return;
      syncToggle();
      if (onChange != null) onChange.run();
    }

    /**
     * The row's settings icon. Leaving for the Keymap page means abandoning the edit being confirmed —
     * a half-ticked list can't be applied — so it <b>cancels this dialog</b> first (and the report
     * behind it, via {@link #openKeymapSettings}), which the tooltip says outright. The report close and
     * the settings open are deferred to a later event so the modal dialog's own loop unwinds first.
     */
    private InplaceButton settingsIconButton(String id) {
      return new InplaceButton("Open Keymap settings at “" + actionName(id) + "” (closes this dialog)",
        SETTINGS_ICON, e -> {
          host.close(DialogWrapper.CANCEL_EXIT_CODE);
          ApplicationManager.getApplication().invokeLater(() -> openKeymapSettings(id));
        });
    }

    private JComponent vcenter(JComponent c) {
      c.setAlignmentY(Component.CENTER_ALIGNMENT);
      return c;
    }

    void setOnChange(Runnable r) { onChange = r; }

    /** A <b>Select all</b> / <b>Deselect all</b> toggle for this list, to be placed <i>above</i> it (so it
     *  stays put while the list scrolls). Same behaviour as the conflict detail's {@link ActionListView}
     *  toggle: it reads "Deselect all" while anything is ticked — which is how these dialogs open, since
     *  every row starts ticked — and clicking it flips the whole list at once. The label follows the
     *  checkboxes, so unticking the last row turns it back into "Select all". */
    ActionLink selectAllLink() {
      selectToggle = new ActionLink("", (ActionListener) e -> {
        boolean select = !anySelected();
        quiet = true;   // one notification for the whole sweep, not one per row
        try {
          for (Row row : rows) row.box.setSelected(select);   // cascades to every shortcut toggle
        }
        finally {
          quiet = false;
        }
        changed();
      });
      selectToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
      syncToggle();
      return selectToggle;
    }

    private boolean anySelected() {
      for (Row row : rows) if (row.box.isSelected()) return true;
      return false;
    }

    private void syncToggle() {
      if (selectToggle != null) selectToggle.setText(anySelected() ? "Deselect all" : "Select all");
    }

    /** The actions with at least one shortcut included — plus any selected action that has no shortcut at
     *  all, which matters to Revert (it can restore a binding this keymap cleared). */
    List<String> checkedIds() {
      List<String> result = new ArrayList<>();
      for (Row row : rows) if (row.box.isSelected()) result.add(row.id);
      return result;
    }

    /** The picked shortcuts per action, in row order; actions contributing nothing are left out. */
    Map<String, List<Shortcut>> pickedShortcuts() {
      Map<String, List<Shortcut>> result = new LinkedHashMap<>();
      for (Row row : rows) {
        List<Shortcut> picked = row.picked();
        if (!picked.isEmpty()) result.put(row.id, picked);
      }
      return result;
    }

    /** The actions whose <b>every</b> shortcut is included — Revert treats those as a clean, whole-action
     *  revert (re-inherit from the parent) rather than a partial one. */
    Set<String> fullyPickedIds() {
      Set<String> result = new LinkedHashSet<>();
      for (Row row : rows) if (row.allPicked()) result.add(row.id);
      return result;
    }

    int pickedShortcutCount() {
      int n = 0;
      for (Row row : rows) n += row.picked().size();
      return n;
    }

    int totalShortcutCount() {
      int n = 0;
      for (Row row : rows) n += row.shortcuts.size();
      return n;
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
    return "This is usually not a conflict — {ide} picks the action that fits where you are (the "
      + "editor, a tool window, a dialog). {ide}'s own Keymap tool treats these the same way and "
      + "doesn't flag them. It only breaks if two can fire in the same place; rebind or remove one "
      + "here if so.";
  }

  // ---- detail formatting helpers --------------------------------------------------------------

  /** The "Shortcut:  [⌘][C]" detail header — a bold label beside the shortcut as {@link Keycaps}. When
   *  {@code caps} is null (a cleared binding / a curated free-text key), {@code fallbackText} is shown
   *  as a gray label instead. */
  private JComponent shortcutHeaderRow(@Nullable JComponent caps, @Nullable String fallbackText) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2)));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    JBLabel label = new JBLabel("Shortcut:");
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    row.add(label);
    if (caps != null) {
      row.add(caps);
    }
    else if (fallbackText != null) {
      JBLabel fallback = new JBLabel(fallbackText);
      fallback.setForeground(grayColour());
      row.add(fallback);
    }
    return row;
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
      case RESOLVE -> "macOS takes this key first, so the {ide} shortcut does not work here.";
      case UNCLASSIFIED -> "macOS may take this key, so the {ide} shortcut might not work here.";
      case DELIBERATE -> "This overlaps a macOS shortcut, but the {ide} shortcut keeps working.";
    };
  }

  private static String sectionBlurb(String title) {
    if (title.equals(SEC_KEYMAP)) {
      return "Shortcuts in this keymap that overlap a macOS system shortcut. Select one to see whether "
        + "it still works and to rebind or remove it. Bindings that come from another plugin or the IDE "
        + "core are tagged with their source; you can change them here just the same.";
    }
    if (title.equals(SEC_IDEA_IGNORED)) {
      return "Overlaps {ide}'s own Keymap tool doesn't flag: keys macOS also uses for switching "
        + "windows ({ide} gets them first while it's in front), plus a couple of macOS features the "
        + "live scan can't see (the Emoji viewer, Dictation). All keep working and are listed for "
        + "completeness; each can still be rebound or removed here (the macOS side is changed in "
        + "System Settings).";
    }
    if (title.equals(SEC_MODIFIED)) {
      return "Shortcuts this keymap declares itself — the ones that differ from its parent keymap "
        + "(added, rebound, or a cleared inherited binding). Use it to review what has been customized. "
        + "Turn on “Show modified only” in the gear menu to hide the conflict sections and see just these.";
    }
    if (title.equals(SEC_INHERITED)) {
      return "Shortcuts this keymap inherits from its parent keymaps — everything bound that it doesn't "
        + "declare itself. Actions with no shortcut aren't listed (binding one is a job for the Keymap "
        + "settings). Select one to rebind or remove it here; the first edit derives an editable copy.";
    }
    return "One shortcut bound to several actions inside the keymap. Not a conflict — {ide} "
      + "picks the action that fits where you are. Shown for reference only.";
  }

  /** The short one-line subtitle shown as a section's first child in the navigator (spec 0003); the
   *  full explanation stays in {@link #sectionBlurb} in the detail pane. */
  private static String sectionSubtitle(String title) {
    if (title.equals(SEC_KEYMAP)) return "Shortcuts that overlap a macOS system shortcut";
    if (title.equals(SEC_IDEA_IGNORED)) return "Overlaps {ide}'s own tool doesn't flag";
    if (title.equals(SEC_MODIFIED)) return "Shortcuts this keymap changed from its parent";
    if (title.equals(SEC_INHERITED)) return "Shortcuts inherited from parent keymaps";
    return "One key bound to several actions — informational";
  }

  private static String macList(ConflictScan.ExternalConflict c) {
    return String.join("; ", c.macOs().stream().map(ConflictScan.SystemShortcut::label).distinct().toList());
  }

  /** Replace the {@code {ide}} placeholder in user-facing text with the running IDE's product name. */
  private static String subIde(String s) {
    return s.replace("{ide}", IDE_NAME);
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

  @Override
  protected com.intellij.openapi.ui.DialogWrapper.@NotNull DialogStyle getStyle() {
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

  /** A vertical (BoxLayout.Y) list that <b>fills the scroll viewport's width</b>, so rows built with a
   *  trailing horizontal glue right-align their keycaps to the true right edge — not to the widest row.
   *  Used for the section-contents listing and the dialog pick lists. */
  private static class WidthTrackingList extends JPanel implements Scrollable {
    WidthTrackingList() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);
      setAlignmentX(Component.LEFT_ALIGNMENT);
    }
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
        r.add(gap());
        r.add(shrinkable(actionLabel(a)));
        // Same right-hand column as the navigator and the dialog lists: glue, then the dimmed meta and
        // source. These rows carry no keycaps (every action here is on the one key in the header), so the
        // meta ends the row.
        r.add(Box.createHorizontalGlue());
        String meta = actionMeta(a.id());   // dimmed keymap/id meta (see actionMeta)
        if (meta != null) r.add(rigid(grayLabel(meta)));
        String src = notableSource(a);
        if (src != null) {
          if (meta != null) r.add(gap());
          r.add(rigid(grayLabel("(" + src + ")")));
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

    /** One row: an X-axis box (not a {@code FlowLayout}) so a trailing glue can push the meta to the
     *  right edge and every part stays vertically centred. The border replaces the vertical gap the
     *  {@code FlowLayout} used to add; horizontal gaps come from {@link #gap()}. */
    private JPanel actionRow() {
      JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
      p.setOpaque(false);
      p.setAlignmentX(Component.LEFT_ALIGNMENT);
      p.setBorder(JBUI.Borders.empty(1, 0));
      return p;
    }

    private Component gap() {
      return Box.createHorizontalStrut(JBUI.scale(6));
    }

    private JBLabel grayLabel(String text) {
      JBLabel l = new JBLabel(text);
      l.setForeground(grayColour());
      return l;
    }
  }

  // ---- renderer -------------------------------------------------------------------------------

  /**
   * Full-width navigator renderer (spec 0003). One horizontal row, laid out with {@link BoxLayout} on the
   * X axis so every part is <b>vertically centred</b>: {@code [icon + name] … glue … [gray id/keymap meta]
   * [shortcut keycaps]}. The glue pushes the meta + keycaps to the <b>right edge</b> — the panel is sized
   * to exactly the viewport width (see {@link #getPreferredSize()}), and the name and meta are
   * {@link #shrinkable} while the keycaps are {@link #rigid}, so an over-long row ellipsizes its text
   * (tooltip = the full text) rather than pushing the keycaps out of alignment. It paints its own
   * selection background.
   */
  private final class Renderer extends JPanel implements TreeCellRenderer {
    private boolean selected;
    private boolean focused;
    private int availWidth;
    private String fullText = "";   // name + meta, for the tooltip a compressed row needs
    private @Nullable String rowActionId;   // the action the trailing settings icon opens, if any

    Renderer() {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(false);
      setBorder(JBUI.Borders.emptyRight(ROW_RIGHT_INSET));   // the icon column's margin from the right edge
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
      this.selected = sel;
      this.focused = tree.hasFocus();
      removeAll();

      Color fg = UIUtil.getTreeForeground(sel, focused);
      Color gray = sel ? fg : UIUtil.getContextHelpForeground();
      layoutRow(((DefaultMutableTreeNode) value).getUserObject(), fg, gray);

      availWidth = availableWidth(tree, ((DefaultMutableTreeNode) value).getLevel());
      // The row's text is ellipsized when it doesn't fit — offer the full text as a tooltip, but only
      // then, so the navigator isn't blanketed in tooltips it doesn't need.
      setToolTipText(availWidth > 0 && super.getPreferredSize().width > availWidth ? fullText : null);
      return this;
    }

    /** Over the trailing settings icon, say what it does — it carries no label. Elsewhere fall back to
     *  the elision tooltip. {@code JTree} translates the event into cell coordinates for us. */
    @Override
    public String getToolTipText(MouseEvent e) {
      if (rowActionId != null && availWidth > 0) {
        int right = availWidth - JBUI.scale(ROW_RIGHT_INSET);
        if (e.getX() >= right - SETTINGS_ICON.getIconWidth() && e.getX() <= right) {
          return "Open Keymap settings at “" + actionName(rowActionId) + "”";
        }
      }
      return super.getToolTipText(e);
    }

    /** Build the row: primary (icon + name) on the left, then glue, then the gray meta and keycaps
     *  right-aligned. Any component with no content for this payload is simply omitted. */
    private void layoutRow(Object p, Color fg, Color gray) {
      Icon icon = null;
      String name = "";
      boolean bold = false;
      String metaRight = null;                 // gray text hugging the right (count / source / id·keymap)
      JComponent caps = null;                  // keycaps, or a gray "(removed)" label

      if (p instanceof Section s) {
        name = subIde(s.title()); bold = true; metaRight = "(" + s.count() + ")";
      }
      else if (p instanceof Subtitle st) {
        name = subIde(sectionSubtitle(st.sectionTitle())); fg = gray;   // gray explanation row
      }
      else if (p instanceof Empty e) {
        icon = e.ok() ? AllIcons.General.InspectionsOK : AllIcons.General.Warning;
        name = subIde(e.message()); fg = gray;
      }
      else if (p instanceof KeymapItem ki) {
        ConflictScan.ExternalConflict c = ki.c();
        icon = needsAttention(c) ? AllIcons.General.Warning : AllIcons.General.Information;
        name = actionsLabel(c.actions()); metaRight = conflictMeta(c.actions()); caps = Keycaps.forKeystroke(c.stroke(), fg);
      }
      else if (p instanceof IdeaIgnoredItem ii) {
        ConflictScan.ExternalConflict c = ii.c();
        icon = needsAttention(c) ? AllIcons.General.Warning : AllIcons.General.Information;
        name = actionsLabel(c.actions()); metaRight = conflictMeta(c.actions()); caps = Keycaps.forKeystroke(c.stroke(), fg);
      }
      else if (p instanceof ConflictAdvice.Supplement s) {
        icon = AllIcons.General.Information; name = s.macSide(); metaRight = s.keys();
      }
      else if (p instanceof ConflictScan.InternalConflict c) {
        icon = AllIcons.General.Information; name = actionsLabel(c.actions()); metaRight = conflictMeta(c.actions());
        caps = Keycaps.forShortcuts(List.of(c.shortcut()), fg);
      }
      else if (p instanceof ModifiedItem mi) {
        icon = AllIcons.General.Information; name = mi.m().action().label(); metaRight = actionMeta(mi.m().action().id());
        caps = mi.m().shortcuts().isEmpty() ? grayLabel("(removed)", gray) : Keycaps.forShortcuts(mi.m().shortcuts(), fg);
      }
      else if (p instanceof InheritedItem it) {
        icon = AllIcons.General.Information; name = it.m().action().label(); metaRight = actionMeta(it.m().action().id());
        caps = it.m().shortcuts().isEmpty() ? grayLabel("(removed)", gray) : Keycaps.forShortcuts(it.m().shortcuts(), fg);
      }

      fullText = metaRight == null ? name : name + "   " + metaRight;

      JLabel main = new JLabel(name, icon, SwingConstants.LEADING);
      main.setForeground(fg);
      if (bold) main.setFont(main.getFont().deriveFont(Font.BOLD));
      main.setAlignmentY(CENTER_ALIGNMENT);
      add(shrinkable(main));

      add(Box.createHorizontalGlue());
      if (metaRight != null) {
        JBLabel m = grayLabel(metaRight, gray);
        m.setAlignmentY(CENTER_ALIGNMENT);
        add(shrinkable(m));
        if (caps != null) add(Box.createHorizontalStrut(JBUI.scale(8)));
      }
      if (caps != null) {
        caps.setAlignmentY(CENTER_ALIGNMENT);
        add(rigid(caps));
      }

      // Rightmost column: the same blue external-link arrow the "Settings…" links carry, for rows that
      // name an action. Clicks are handled by the tree's mouse listener (a renderer can't receive them)
      // — see settingsIconHit(). Rows with no single action leave the column empty, and the strut keeps
      // the keycaps' right edge aligned with theirs.
      rowActionId = rowActionId(p);
      add(Box.createHorizontalStrut(JBUI.scale(ICON_GAP)));
      if (rowActionId != null) {
        JLabel link = new JLabel(SETTINGS_ICON);
        link.setAlignmentY(CENTER_ALIGNMENT);
        add(rigid(link));
      }
      else {
        add(Box.createHorizontalStrut(SETTINGS_ICON.getIconWidth()));
      }
    }

    private JBLabel grayLabel(String text, Color gray) {
      JBLabel l = new JBLabel(text);
      l.setForeground(gray);
      return l;
    }

    /** Size the cell to the viewport's right edge — <b>exactly</b>, not just when the content is
     *  narrower. A row that would be wider is clamped too, so the keycaps stay flush right (the name,
     *  then the meta, gives up the width and ellipsizes) instead of the tree scrolling horizontally
     *  and every row's keycaps ending at a different x. */
    @Override
    public Dimension getPreferredSize() {
      Dimension d = super.getPreferredSize();
      if (availWidth > 0) d.width = availWidth;
      return d;
    }

    /** Width from this node's left edge to the viewport's right edge, less a 2px slack so a rounding
     *  difference can't trip the horizontal scrollbar (the visible right margin is the panel's own
     *  right border). Indent is approximated from the tree's per-level step (no {@code getRowBounds},
     *  which would recurse). */
    private int availableWidth(JTree tree, int level) {
      int vw = treeScroll != null ? treeScroll.getViewport().getWidth() : tree.getVisibleRect().width;
      if (vw <= 0) return 0;
      int perLevel = JBUI.scale(20);
      if (tree.getUI() instanceof BasicTreeUI b) {
        int p = b.getLeftChildIndent() + b.getRightChildIndent();
        if (p > 0) perLevel = p;
      }
      int indent = tree.getInsets().left + level * perLevel;
      return Math.max(0, vw - indent - JBUI.scale(2));
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (selected) {
        g.setColor(UIUtil.getTreeBackground(true, focused));
        g.fillRect(0, 0, getWidth(), getHeight());
      }
      super.paintComponent(g);
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
  private final class ShortcutInputDialog extends DialogWrapper {
    private static final int MODIFIER_MASK = InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                                           | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    // Keys that can't be typed/pressed into the field (bare ones drive the dialog); pick via a radio.
    // Space belongs here too: typed into the field it is a single blank that `trim()` throws away, and
    // pressed bare it falls through to the field as ordinary typing — so it was unbindable without this.
    // Its label is the word "Space", because that is what the platform renders for VK_SPACE on macOS
    // (MacKeymapUtil.getKeyText hardcodes it, unlike ↩ ⎋ ⌫ ⇥) — so the radio matches the keycap.
    private static final int[] SPECIAL_CODES = {KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE,
                                               KeyEvent.VK_TAB, KeyEvent.VK_SPACE};
    private static final String[] SPECIAL_SYMBOLS = {"↩", "⎋", "⌫", "⇥", "Space"};

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
    private final JPanel previewCaps = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), 0));
    private final JBLabel status = new JBLabel();
    private final JBLabel suggestNote = new JBLabel();
    private final ButtonGroup specialGroup = new ButtonGroup();
    private JRadioButton[] specialRadios;
    private int keyCode = KeyEvent.VK_UNDEFINED;  // the chosen key; the source of truth for build()
    private boolean settingText;                  // true while the field is set programmatically
    private KeyStroke result;
    private EditActionList checkList;              // editable pick list, shown by default
    private JComponent listScroll;                 // scroll host for checkList; "Hide actions" collapses it
    private @Nullable ActionLink selectAll;        // only when more than one mapping is moving

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
      previewCaps.setOpaque(false);   // rebuilt live in updateResult() as keycaps

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

      // Only the shortcuts on the key being rebound start ticked — the scope this operation always had.
      // Another of an action's shortcuts can be ticked to move it onto the new key as well.
      checkList = new EditActionList(movingIds, false, this, sc -> onStroke(sc, original));
      listScroll = cappedScroll(checkList);   // shown by default (spec 0003)
      JBLabel movesCount = new JBLabel();
      Runnable count = () -> movesCount.setText(
        checkList.pickedShortcutCount() + " shortcut(s) across "
        + checkList.pickedShortcuts().size() + " of " + affected + " action(s) will move.");
      checkList.setOnChange(count);
      count.run();

      JPanel suggestRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
      suggestRow.setOpaque(false);
      suggestRow.add(new ActionLink("Suggest a shortcut", (ActionListener) e -> applySuggestion()));
      suggestRow.add(suggestNote);

      FormBuilder form = FormBuilder.createFormBuilder()
        .addLabeledComponent("Current:", Keycaps.forKeystroke(original, null))
        .addLabeledComponent("Key:", keyRow)
        .addLabeledComponent("Modifiers:", modifiers)
        .addComponent(suggestRow)
        .addLabeledComponent("New:", previewCaps)
        .addComponent(status)
        .addComponent(movesCount);
      if (affected > 1) {   // a one-row list has nothing to select all of
        selectAll = checkList.selectAllLink();
        form.addComponent(selectAll);
      }
      // The fixed rows go NORTH (pinned to the top-left at their preferred height); the list goes
      // CENTER so it absorbs whatever height is left and scrolls inside it. See listLayout().
      JPanel wrap = listLayout(form.getPanel(), listScroll);
      if (notice != null) {
        JBLabel note = new JBLabel(notice);
        note.setForeground(UIUtil.getContextHelpForeground());
        wrap.add(note, BorderLayout.SOUTH);
      }
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
      previewCaps.removeAll();
      if (ks == null) {
        JBLabel dash = new JBLabel("—");
        dash.setForeground(UIUtil.getContextHelpForeground());
        previewCaps.add(dash);
      }
      else {
        previewCaps.add(Keycaps.forKeystroke(ks, null));
      }
      previewCaps.revalidate();
      previewCaps.repaint();
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
          + "Allowed (" + IDE_NAME + " permits it), but usually unintended.");
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

    /** Compute and fill in a suggested shortcut, replacing whatever the fields currently hold. Used by
     *  the in-dialog "Suggest a shortcut" link, and applied automatically when this dialog is opened via
     *  the navigator's own "Suggest…" link (see {@link #suggestAndRebind}). */
    private void applySuggestion() {
      ShortcutSuggester.Suggestion s = ShortcutSuggester.suggest(keymap, movingIds);
      if (s == null) {
        suggestNote.setText("No candidate shortcut found.");
        suggestNote.setForeground(warnColour());
        return;
      }
      int mods = s.stroke().getModifiers();
      control.setSelected((mods & InputEvent.CTRL_DOWN_MASK) != 0);
      option.setSelected((mods & InputEvent.ALT_DOWN_MASK) != 0);
      shift.setSelected((mods & InputEvent.SHIFT_DOWN_MASK) != 0);
      command.setSelected((mods & InputEvent.META_DOWN_MASK) != 0);
      setKey(s.stroke().getKeyCode());
      updateResult();
      suggestNote.setText(s.free()
        ? "✓ Suggested a shortcut that is completely free."
        : "⚠ No fully free shortcut found — this one double-binds with " + actionName(s.sharedWith().get(0))
          + (s.sharedWith().size() > 1 ? " +" + (s.sharedWith().size() - 1) + " more" : "")
          + ", best-guessed not to interfere. Please confirm before accepting.");
      suggestNote.setForeground(s.free() ? okColour() : warnColour());
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
        // Space counts: bare, it types a space like any other printable key, so it earns the same warning.
        case KeyEvent.VK_SPACE,
             KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_COMMA,
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

    /** The shortcuts the user left ticked, per action — exactly what moves to the new keystroke. */
    Map<String, List<Shortcut>> pickedShortcuts() {
      if (checkList != null) return checkList.pickedShortcuts();
      Map<String, List<Shortcut>> all = new LinkedHashMap<>();   // no list built (shouldn't happen)
      for (String id : movingIds) {
        List<Shortcut> on = new ArrayList<>();
        for (Shortcut sc : keymap.getShortcuts(id)) if (onStroke(sc, original)) on.add(sc);
        if (!on.isEmpty()) all.put(id, on);
      }
      return all;
    }

    @Override
    protected JComponent createDoNotAskCheckbox() {
      JCheckBox hide = new JCheckBox("Hide actions");   // list shown by default; this collapses it
      hide.addActionListener(e -> {
        boolean show = !hide.isSelected();
        listScroll.setVisible(show);
        if (selectAll != null) selectAll.setVisible(show);   // no stray toggle over a collapsed list
        resizeToFit(listScroll);
      });
      return hide;
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
