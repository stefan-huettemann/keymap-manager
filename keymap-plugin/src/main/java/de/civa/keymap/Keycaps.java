package de.civa.keymap;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders shortcuts as macOS-style <b>keycaps</b> — each modifier and the key in its own rounded
 * frame, matching Settings → Keymap (spec 0003). Public-API only: the glyphs come from
 * {@link KeymapUtil#getKeystrokeText}, then are split into per-cap tokens, so the exact platform
 * text (incl. umlaut and arrow glyphs) is preserved and the verifier stays clean.
 */
final class Keycaps {

  /** macOS modifier glyphs, in the order {@code getKeystrokeText} emits them: ⌃ ⌥ ⇧ ⌘. */
  private static final String MODIFIER_GLYPHS = "⌃⌥⇧⌘";

  private Keycaps() {}

  // ---- tokenisation ---------------------------------------------------------------------------

  /** Split one keystroke's rendered text ("⌥⌘Y") into per-cap tokens (["⌥", "⌘", "Y"]). */
  static List<String> tokens(KeyStroke ks) {
    String s = KeymapUtil.getKeystrokeText(ks);
    List<String> tokens = new ArrayList<>();
    int i = 0;
    while (i < s.length() && MODIFIER_GLYPHS.indexOf(s.charAt(i)) >= 0) {
      tokens.add(String.valueOf(s.charAt(i)));
      i++;
    }
    if (i < s.length()) tokens.add(s.substring(i));   // the key glyph(s): "Y", "F5", "↩", "Space"
    return tokens;
  }

  // ---- components -----------------------------------------------------------------------------

  /** A transparent, left-to-right row of keycaps for a list of shortcuts (default colours). */
  static JComponent forShortcuts(List<Shortcut> shortcuts) {
    return forShortcuts(shortcuts, null, null);
  }

  /**
   * A transparent row of keycaps for a list of shortcuts. Several shortcuts are separated by a faint
   * "/". {@code fg}/{@code border} override the glyph and frame colours (used by the tree renderer so
   * caps stay legible on the selection background); pass {@code null} for the default label colours.
   */
  static JComponent forShortcuts(List<Shortcut> shortcuts, @Nullable Color fg, @Nullable Color border) {
    JPanel row = row();
    boolean first = true;
    for (Shortcut sc : shortcuts) {
      if (!first) row.add(separator(fg));
      first = false;
      addShortcut(row, sc, fg, border);
    }
    return row;
  }

  /** A transparent row of keycaps for a single shortcut (default colours). */
  static JComponent forShortcut(Shortcut sc) {
    JPanel row = row();
    addShortcut(row, sc, null, null);
    return row;
  }

  /** A transparent row of keycaps for a bare keystroke (e.g. a conflict's key). */
  static JComponent forKeystroke(KeyStroke ks, @Nullable Color fg, @Nullable Color border) {
    JPanel row = row();
    for (String t : tokens(ks)) row.add(cap(t, fg, border));
    return row;
  }

  /** Append the caps for one shortcut into {@code row}; a two-stroke chord renders as two cap groups. */
  private static void addShortcut(JPanel row, Shortcut sc, @Nullable Color fg, @Nullable Color border) {
    if (sc instanceof KeyboardShortcut ks) {
      for (String t : tokens(ks.getFirstKeyStroke())) row.add(cap(t, fg, border));
      if (ks.getSecondKeyStroke() != null) {
        row.add(chordGap());
        for (String t : tokens(ks.getSecondKeyStroke())) row.add(cap(t, fg, border));
      }
    }
    else {
      row.add(cap(KeymapUtil.getShortcutText(sc), fg, border));   // mouse / gesture: one plain cap
    }
  }

  // ---- building blocks ------------------------------------------------------------------------

  private static JPanel row() {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), 0));
    p.setOpaque(false);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    return p;
  }

  private static JLabel cap(String glyph, @Nullable Color fg, @Nullable Color border) {
    JLabel label = new JLabel(glyph, SwingConstants.CENTER);
    label.setFont(JBUI.Fonts.smallFont());
    label.setForeground(fg != null ? fg : UIUtil.getLabelForeground());
    label.setBorder(new KeycapBorder(border));
    return label;
  }

  /** A small horizontal gap between the two cap groups of a chord shortcut (e.g. ⌘K ⌘D). */
  private static Component chordGap() {
    return Box.createHorizontalStrut(JBUI.scale(3));
  }

  /** A faint "/" between alternative shortcuts of the same action. */
  private static JLabel separator(@Nullable Color fg) {
    JLabel s = new JLabel("/");
    s.setForeground(fg != null ? fg : UIUtil.getContextHelpForeground());
    return s;
  }

  /** Rounded 1px frame with inner padding — the "key" look. */
  private static final class KeycapBorder extends AbstractBorder {
    private final @Nullable Color override;

    KeycapBorder(@Nullable Color override) {
      this.override = override;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(override != null ? override : JBColor.border());
        int arc = JBUI.scale(6);
        g2.drawRoundRect(x, y, width - JBUI.scale(1), height - JBUI.scale(1), arc, arc);
      }
      finally {
        g2.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.insets(1, 5);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      Insets i = getBorderInsets(c);
      insets.set(i.top, i.left, i.bottom, i.right);
      return insets;
    }
  }
}
