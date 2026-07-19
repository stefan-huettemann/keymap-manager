package de.civa.keymap;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
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
 * option, restart.
 */
public final class NationalLayoutCheck implements KeymapManagerListener, AppLifecycleListener {
  private static final String KEYMAP_NAME = "MacBook Pro DE";
  private static final String NOTIFICATION_GROUP = "MacBook Pro DE Keymap";
  private static final AtomicBoolean NOTIFIED = new AtomicBoolean();

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
    if (keymap == null || !SystemInfoRt.isMac || !KEYMAP_NAME.equals(keymap.getName())) {
      return;
    }
    if (NationalKeyboardSupport.getInstance().getEnabled()) {
      return;
    }
    if (!NOTIFIED.compareAndSet(false, true)) {
      return;
    }
    Notification notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(
        "MacBook Pro DE keymap needs national keyboard layout support",
        "This keymap binds keys of the German layout (Ä Ö Ü ß + # <), which only work while " +
        "\"Use national keyboard layouts for shortcuts\" is enabled (Settings → Keymap). " +
        "Enabling requires an IDE restart.",
        NotificationType.WARNING);
    notification.addAction(NotificationAction.createSimple("Enable and restart", () -> enableAndRestart(notification)));
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
