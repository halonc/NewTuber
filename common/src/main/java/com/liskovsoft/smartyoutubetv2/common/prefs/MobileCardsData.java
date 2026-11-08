package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * NEWTUBE(card-density): the one look-and-feel switch this fork adds to the phone feed - compact
 * cards. Kept out of {@link MainUIData} on purpose: that class persists a positional string array
 * whose indices are part of the on-disk format, so adding a field there would need a migration.
 *
 * <p>Stored in {@link AppPrefs} like the rest of the phone settings, so it follows the account
 * profile the same way the fresh-home window does.</p>
 */
public final class MobileCardsData {
    private static final String COMPACT_CARDS = "compact_cards";

    private MobileCardsData() {
    }

    public static boolean isCompactCards(Context context) {
        return AppPrefs.instance(context).getBoolean(COMPACT_CARDS, false);
    }

    public static void setCompactCards(Context context, boolean value) {
        AppPrefs.instance(context).putBoolean(COMPACT_CARDS, value);
    }
}
