package de.civa.keymap;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A grouped, explained view of how the MacBook Pro DE keymap overlaps macOS system
 * shortcuts — the friendly counterpart to IDEA's bare "N conflicts with macOS" count.
 * Groups by {@link KnownConflict.Category} and, for each, says whether action is needed
 * and how to resolve it.
 */
public final class ConflictReportDialog extends DialogWrapper {

  public ConflictReportDialog(@Nullable Project project) {
    super(project);
    setTitle("MacBook Pro DE — macOS Shortcut Conflicts");
    setResizable(true);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JEditorPane pane = new JEditorPane("text/html", buildHtml());
    pane.setEditable(false);
    pane.setBorder(JBUI.Borders.empty(8));
    pane.setBackground(UIUtil.getPanelBackground());
    JScrollPane scroll = new JScrollPane(pane,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(JBUI.scale(620), JBUI.scale(460)));
    scroll.setBorder(JBUI.Borders.empty());
    pane.setCaretPosition(0);
    return scroll;
  }

  private static String buildHtml() {
    Map<KnownConflict.Category, java.util.List<KnownConflict>> byCat = new LinkedHashMap<>();
    for (KnownConflict.Category c : KnownConflict.Category.values()) {
      byCat.put(c, new java.util.ArrayList<>());
    }
    for (KnownConflict k : KnownConflict.ALL) {
      byCat.get(k.category()).add(k);
    }

    StringBuilder sb = new StringBuilder("<html><body style='width:560px'>");
    sb.append("<p>IDEA reports these as \"conflicts with macOS\" because the shortcut also ")
      .append("belongs to a macOS system function. Most are intentional; only the middle ")
      .append("group may need a change.</p>");

    for (var entry : byCat.entrySet()) {
      List<KnownConflict> items = entry.getValue();
      if (items.isEmpty()) continue;
      sb.append("<h3>").append(entry.getKey().title).append("</h3>");
      sb.append("<table cellspacing='0' cellpadding='4' style='width:100%'>");
      for (KnownConflict k : items) {
        sb.append("<tr><td valign='top' style='white-space:nowrap'><b>").append(k.keys()).append("</b></td>")
          .append("<td valign='top'>")
          .append("macOS: ").append(k.macOsFunction()).append("<br>")
          .append("IDEA: <code>").append(k.ideaAction()).append("</code><br>")
          .append("<span style='color:gray'>").append(k.note()).append("</span>")
          .append("</td></tr>");
      }
      sb.append("</table>");
    }
    sb.append("<p style='color:gray'>Tip: to silence a specific overlap in IDEA, open ")
      .append("Settings → Keymap, right-click the shortcut and choose \"Do not show again\".</p>");
    sb.append("</body></html>");
    return sb.toString();
  }

  @Override
  protected com.intellij.openapi.ui.DialogWrapper.@Nullable DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }
}
