package de.civa.keymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

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
  private final Project project;
  private Keymap keymap;
  private ConflictScan scan;
  private Tree tree;
  private JEditorPane detail;
  private JBLabel summaryText;
  private JBLabel summaryIcon;

  public ConflictReportDialog(@Nullable Project project) {
    super(project);
    this.project = project;
    keymap = KeymapManager.getInstance().getActiveKeymap();
    scan = ConflictScan.of(keymap);
    setTitle("Review Keymap Conflicts");
    setResizable(true);
    init();
  }

  /** Typed tree payloads; the renderer and detail pane switch on these. */
  private record Section(String title, int count) {}
  private record Empty(String message) {}
  private record KeymapItem(ConflictScan.ExternalConflict c) {}
  private record OutsideItem(ConflictScan.ExternalConflict c) {}

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

  private JComponent selectorPane() {
    ComboBox<Keymap> combo = new ComboBox<>(KeymapManagerEx.getInstanceEx().getAllKeymaps());
    combo.setSelectedItem(keymap);
    combo.setRenderer(SimpleListCellRenderer.create("", Keymap::getPresentableName));
    combo.addActionListener(e -> {
      if (combo.getSelectedItem() instanceof Keymap k && !k.equals(keymap)) {
        keymap = k;
        refresh();
      }
    });

    JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    row.setBorder(JBUI.Borders.empty(8, 10, 6, 10));
    row.add(new JBLabel("Keymap:"), BorderLayout.WEST);
    row.add(combo, BorderLayout.CENTER);
    return row;
  }

  // ---- tree model -----------------------------------------------------------------------------

  private DefaultMutableTreeNode buildRoot() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    if (!scan.outsideConflicts.isEmpty()) {
      DefaultMutableTreeNode outside = new DefaultMutableTreeNode(
        new Section("Conflicts outside this keymap", scan.outsideConflicts.size()));
      for (ConflictScan.ExternalConflict c : scan.outsideConflicts) {
        outside.add(new DefaultMutableTreeNode(new OutsideItem(c)));
      }
      root.add(outside);
    }

    DefaultMutableTreeNode keymapNode = new DefaultMutableTreeNode(
      new Section("Keymap conflicts", scan.keymapConflicts.size() + ConflictAdvice.SUPPLEMENT.size()));
    if (!scan.jbrApiAvailable) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("macOS scan unavailable on this runtime.")));
    }
    else if (scan.keymapConflicts.isEmpty()) {
      keymapNode.add(new DefaultMutableTreeNode(new Empty("No conflicts with macOS. Nothing to change.")));
    }
    for (ConflictScan.ExternalConflict c : scan.keymapConflicts) {
      keymapNode.add(new DefaultMutableTreeNode(new KeymapItem(c)));
    }
    for (ConflictAdvice.Supplement s : ConflictAdvice.SUPPLEMENT) {
      keymapNode.add(new DefaultMutableTreeNode(s));
    }
    root.add(keymapNode);

    DefaultMutableTreeNode dbl = new DefaultMutableTreeNode(
      new Section("Double-bound keys — informational, not conflicts", scan.internal.size()));
    for (ConflictScan.InternalConflict c : scan.internal) dbl.add(new DefaultMutableTreeNode(c));
    root.add(dbl);

    return root;
  }

  private void refresh() {
    scan = ConflictScan.of(keymap);
    tree.setModel(new DefaultTreeModel(buildRoot()));
    expandActionableSections();
    selectFirstConflict();
    updateSummary();
  }

  /** Expand everything except the (long, low-value) double-bound list. */
  private void expandActionableSections() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      Object p = section.getUserObject();
      if (p instanceof Section s && s.title().startsWith("Double-bound")) continue;
      tree.expandPath(new TreePath(section.getPath()));
    }
  }

  private void selectFirstConflict() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode section = (DefaultMutableTreeNode) root.getChildAt(i);
      Object p = section.getUserObject();
      if (p instanceof Section s && s.title().startsWith("Double-bound")) continue;
      if (section.getChildCount() > 0) {
        DefaultMutableTreeNode first = (DefaultMutableTreeNode) section.getFirstChild();
        tree.setSelectionPath(new TreePath(first.getPath()));
        return;
      }
    }
  }

  private @Nullable Object payloadOf(@Nullable TreePath path) {
    return path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
  }

  // ---- links ----------------------------------------------------------------------------------

  private void onLink(HyperlinkEvent e) {
    if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
    String href = e.getDescription();
    Object payload = payloadOf(tree.getSelectionPath());
    if ("civa:settings".equals(href)) {
      openKeymapSettings();
    }
    else if ("civa:remove".equals(href) && payload instanceof KeymapItem ki) {
      if (keymap.canModify()) {
        KeyboardShortcut sc = new KeyboardShortcut(ki.c().stroke(), null);
        for (ConflictScan.ActionRef a : ki.c().actions()) keymap.removeShortcut(a.id(), sc);
        refresh();
      }
      else {
        openKeymapSettings();
      }
    }
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
      appendConflict(sb, ki.c(), true);
    }
    else if (payload instanceof OutsideItem oi) {
      appendConflict(sb, oi.c(), false);
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

  private void appendConflict(StringBuilder sb, ConflictScan.ExternalConflict c, boolean owned) {
    sb.append(keyHead(KeymapUtil.getKeystrokeText(c.stroke())))
      .append(statusBadge(c))
      .append(factsTable(new String[][]{
        {owned ? "IntelliJ action" : "Used by", actionList(c.actions())},
        {"macOS", escape(macList(c))}}));
    if (!owned) {
      sb.append(grayBlock("This shortcut comes from another plugin or from IntelliJ itself, not from "
        + "this keymap, so it can't be changed here."));
    }
    sb.append(callout("What to do", escape(c.advice().note())))
      .append(links(owned));
  }

  private static String links(boolean owned) {
    String settings = "<a href='civa:settings'>Open Keymap settings</a>";
    String body = owned ? "<a href='civa:remove'>Remove this shortcut</a> &nbsp;&nbsp;•&nbsp;&nbsp; " + settings
                        : settings;
    return "<div style='margin:12px 0 0 0'>" + body + "</div>";
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
        renderConflict(ki.c());
      }
      else if (p instanceof OutsideItem oi) {
        renderConflict(oi.c());
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

    private void renderConflict(ConflictScan.ExternalConflict c) {
      setIcon(needsAttention(c) ? AllIcons.General.Warning : AllIcons.General.Information);
      append(KeymapUtil.getKeystrokeText(c.stroke()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      List<ConflictScan.ActionRef> a = c.actions();
      String suffix = a.isEmpty() ? "" : a.get(0).label() + (a.size() > 1 ? " +" + (a.size() - 1) : "");
      append("  " + suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
