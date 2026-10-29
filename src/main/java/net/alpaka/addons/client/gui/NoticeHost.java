package net.alpaka.addons.client.gui;

/**
 * A screen that draws the mod's notices over itself.
 *
 * Notices are normally drawn with the HUD, which lies under any open screen. A screen that covers
 * the HUD - the config menu and the windows opened from it - would hide them there, so a notice
 * sent while it is open (Send Test Notification, a confirmation) would never be seen. Such a screen
 * implements this interface and calls
 * {@link net.alpaka.addons.features.notification.AlpakaNotifications#renderOverScreen} at the end
 * of its own drawing; while it is open the HUD leaves the notices out, so they are never drawn
 * twice, once under the screen and once over it.
 *
 * A marker only: being a host is the whole contract, and there is nothing to implement.
 */
public interface NoticeHost {
}
