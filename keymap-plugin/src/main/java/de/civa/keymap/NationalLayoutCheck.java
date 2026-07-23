package de.civa.keymap;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.NationalKeyboardSupport;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "MacBook Pro DE" keymap binds keys of the German (T1) layout — Ä Ö Ü ß + # < —
 * which the JetBrains Runtime only reports while national keyboard layout support
 * ({@code com.sun.awt.use.national.layouts}, on by default on macOS) is enabled.
 * Warns when the keymap is selected while that support is off, and offers the same
 * fix the Settings → Keymap checkbox applies: persist the setting, write the VM
 * option, restart. Also surfaces a "Review keymap conflicts" action that opens a
 * live, grouped, explained conflict report ({@link ConflictScan}) — a friendlier
 * take on IDEA's bare "N conflicts with macOS" count.
 */
public final class NationalLayoutCheck implements KeymapManagerListener, AppLifecycleListener {
  private static final String KEYMAP_NAME = "MacBook Pro DE";
  private static final String NOTIFICATION_GROUP = "MacBook Pro DE Keymap";
  // Two independent one-shots so the critical layout-off warning is never pre-empted by the generic
  // nudge: they apply to different keymaps and must not share a latch.
  private static final AtomicBoolean NUDGED = new AtomicBoolean();
  private static final AtomicBoolean WARNED = new AtomicBoolean();

  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    KeymapManager manager = KeymapManager.getInstance();
    if (manager != null) {
      checkKeymap(manager.getActiveKeymap());
    }
  }

  @Override
  public void activeKeymapChanged(@Nullable Keymap keymap) {
    checkKeymap(keymap);
  }

  private static void checkKeymap(@Nullable Keymap keymap) {
    if (keymap == null || !SystemInfoRt.isMac) {
      return;
    }
    // The national-layout warning only applies to our keymap — it is the one binding German keys.
    // For any other active keymap we still offer the conflict report, which scans whatever is active.
    boolean ownKeymap = KEYMAP_NAME.equals(keymap.getName());
    boolean layoutOff = ownKeymap && !NationalKeyboardSupport.getInstance().getEnabled();
    // Latch per kind of notification. The layout-off warning uses its own latch, so a nudge shown
    // first (e.g. a non-German keymap active at startup) can't suppress the warning when the user
    // later switches to this keymap.
    if (!(layoutOff ? WARNED : NUDGED).compareAndSet(false, true)) {
      return;
    }
    Notification notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(
        layoutOff
          ? "MacBook Pro DE keymap needs national keyboard layout support"
          : "Manage keymap conflicts",
        layoutOff
          ? "This keymap binds keys of the German layout (Ä Ö Ü ß + # <), which only work while " +
            "\"Use national keyboard layouts for shortcuts\" is enabled (Settings → Keymap). " +
            "Enabling requires an IDE restart."
          : "The active keymap \"" + keymap.getPresentableName() + "\" may have shortcuts that " +
            "overlap macOS system shortcuts or each other. Review which are intentional and which " +
            "need a change.",
        layoutOff ? NotificationType.WARNING : NotificationType.INFORMATION);
    if (layoutOff) {
      notification.addAction(NotificationAction.createSimple("Enable and restart", () -> enableAndRestart(notification)));
    }
    notification.addAction(new NotificationAction("Manage keymap conflicts") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification n) {
        new ConflictReportDialog(e.getProject()).show();
      }
    });
    notification.addAction(NotificationAction.createSimple("Dismiss", notification::expire));
    notification.notify(null);
  }

  private static void enableAndRestart(Notification notification) {
    NationalKeyboardSupport.getInstance().setEnabled(true);
    try {
      VMOptions.setProperty(NationalKeyboardSupport.getVMOption(), "true");
      notification.expire();
      ApplicationManager.getApplication().invokeLater(
        () -> ApplicationManager.getApplication().restart(),
        ModalityState.nonModal());
    }
    catch (IOException e) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(
          "Could not write VM options",
          "Enable the option manually: Settings → Keymap → " +
          "\"Use national keyboard layouts for shortcuts\". (" + e.getMessage() + ")",
          NotificationType.ERROR)
        .notify(null);
    }
  }
}
