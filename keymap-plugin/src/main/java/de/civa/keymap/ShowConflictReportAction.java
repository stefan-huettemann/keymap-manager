package de.civa.keymap;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * Tools-menu entry point for the live keymap-conflict report, so it stays reachable after the
 * activation notification is dismissed (also found via Find Action).
 */
public final class ShowConflictReportAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new ConflictReportDialog(e.getProject()).show();
  }
}
