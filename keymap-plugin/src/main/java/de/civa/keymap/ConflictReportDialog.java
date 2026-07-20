package de.civa.keymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

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
import java.awt.Dimension;
import java.util.List;

/**
 * A foldable, navigable view of the active keymap's shortcut conflicts, produced live by
 * {@link ConflictScan}. Three sections, ordered by how much the reader should care:
 * <ol>
 *   <li><b>Conflicts outside this keymap</b> — shortcuts from other plugins or the IDE that sit on
 *       a key macOS also uses. We can't change them here, but we explain why they may not work.
 *       Hidden when empty.</li>
 *   <li><b>Keymap conflicts</b> — this keymap's own shortcuts that overlap macOS. Each can be
 *       removed or changed from here.</li>
 *   <li><b>Double-bound keys</b> — one shortcut on several actions inside the keymap. Not a
 *       conflict; shown for reference.</li>
 * </ol>
 * A {@link Tree} navigates; a detail pane explains the selected row and offers the fix links.
 */
public final class ConflictReportDialog extends DialogWrapper {
  private final Project project;
  private final Keymap keymap;
  private ConflictScan scan;
  private Tree tree;
  private JEditorPane detail;

  public ConflictReportDialog(@Nullable Project project) {
    super(project);
    this.project = project;
    keymap = KeymapManager.getInstance().getActiveKeymap();
    scan = ConflictScan.of(keymap);
    setTitle(keymap.getPresentableName() + " — Keymap Conflicts");
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

    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.44f);
    splitter.setFirstComponent(new JBScrollPane(tree));
    splitter.setSecondComponent(new JBScrollPane(detail));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(summaryLabel(), BorderLayout.NORTH);
    panel.add(splitter, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(JBUI.scale(800), JBUI.scale(560)));
    return panel;
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

  private JComponent summaryLabel() {
    int total = scan.keymapConflicts.size() + scan.outsideConflicts.size();
    long broken = scan.keymapConflicts.stream().filter(c -> needsAttention(c)).count()
                + scan.outsideConflicts.stream().filter(c -> needsAttention(c)).count();
    String text;
    if (!scan.jbrApiAvailable) {
      text = "The macOS shortcut check is unavailable on this runtime.";
    }
    else if (total == 0) {
      text = "<b>No overlaps with macOS system shortcuts.</b>";
    }
    else if (broken == 0) {
      text = "<b>Nothing broken.</b> " + total + " shortcut(s) overlap macOS but keep working.";
    }
    else {
      text = "<b>" + broken + " shortcut(s) need attention</b> — macOS takes the key first. "
        + (total - broken) + " more overlap but keep working.";
    }
    JBLabel label = new JBLabel("<html>" + text + "</html>");
    label.setBorder(JBUI.Borders.empty(6, 10, 8, 10));
    return label;
  }

  private static boolean needsAttention(ConflictScan.ExternalConflict c) {
    return c.advice().category() != ConflictAdvice.Category.DELIBERATE;
  }

  // ---- detail pane ----------------------------------------------------------------------------

  private void showDetail(@Nullable Object payload) {
    if (payload == null) { detail.setText(""); return; }
    StringBuilder sb = new StringBuilder("<html><body style='margin:0'>");
    if (payload instanceof Section s) {
      sb.append("<h3>").append(escape(s.title())).append("</h3>")
        .append("<p style='color:gray'>").append(escape(sectionBlurb(s.title()))).append("</p>");
    }
    else if (payload instanceof Empty e) {
      sb.append("<p style='color:gray'>").append(escape(e.message())).append("</p>");
    }
    else if (payload instanceof KeymapItem ki) {
      appendKeymapItem(sb, ki.c());
    }
    else if (payload instanceof OutsideItem oi) {
      appendOutsideItem(sb, oi.c());
    }
    else if (payload instanceof ConflictAdvice.Supplement s) {
      sb.append(head(s.keys()))
        .append(row("IntelliJ", escape(s.ideaSide())))
        .append(row("macOS", escape(s.macSide())))
        .append("<p>").append(escape(s.note())).append("</p>");
    }
    else if (payload instanceof ConflictScan.InternalConflict c) {
      sb.append(head(KeymapUtil.getShortcutText(c.shortcut())))
        .append("<p style='color:gray'>").append(c.actions().size())
        .append(" actions use this shortcut. This is not a conflict — IntelliJ picks the right one "
          + "based on where you are (editor, a tool window, a dialog).</p>")
        .append("<p>").append(actionList(c.actions())).append("</p>");
    }
    sb.append("</body></html>");
    detail.setText(sb.toString());
    detail.setCaretPosition(0);
  }

  private void appendKeymapItem(StringBuilder sb, ConflictScan.ExternalConflict c) {
    sb.append(head(KeymapUtil.getKeystrokeText(c.stroke())))
      .append("<p style='margin-top:0;color:gray'>").append(escape(status(c))).append("</p>")
      .append(row("IntelliJ action", actionList(c.actions())))
      .append(row("macOS uses this key for", escape(macList(c))))
      .append("<p>").append(escape(c.advice().note())).append("</p>")
      .append("<p><a href='civa:remove'>Remove this shortcut</a>"
        + " &nbsp;•&nbsp; <a href='civa:settings'>Open Keymap settings</a></p>");
  }

  private void appendOutsideItem(StringBuilder sb, ConflictScan.ExternalConflict c) {
    sb.append(head(KeymapUtil.getKeystrokeText(c.stroke())))
      .append("<p style='margin-top:0;color:gray'>").append(escape(status(c))).append("</p>")
      .append(row("Used by", actionList(c.actions())))
      .append(row("macOS uses this key for", escape(macList(c))))
      .append("<p>This shortcut comes from another plugin or from IntelliJ itself, not from this "
        + "keymap, so it can't be changed here. ").append(escape(c.advice().note())).append("</p>")
      .append("<p><a href='civa:settings'>Open Keymap settings</a></p>");
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

  private static String head(String title) {
    return "<h2 style='margin-bottom:2px'>" + escape(title) + "</h2>";
  }

  private static String row(String label, String valueHtml) {
    return "<p style='margin:3px 0'><b>" + escape(label) + ":</b> " + valueHtml + "</p>";
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
