package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.RecentHomeCards;

import java.util.ArrayList;
import java.util.List;

/**
 * NEWTUBE(fresh-home): the settings glue and persistence for Home's "already shown" window.
 *
 * <p>The window itself ({@link RecentHomeCards}) is plain state; this class only gives it the app's
 * own prefs file to live in. {@link AppPrefs} is per account profile, so the window belongs to the
 * signed-in user and a sign out or an account switch cannot leak one account's history into another -
 * the same rule FeedCache follows.</p>
 */
public class HomeFeedData {
    private static final String RECENT_HOME_CARDS = "recent_home_cards";
    private static final String FRESH_HOME_ENABLED = "fresh_home_enabled";

    @SuppressLint("StaticFieldLeak")
    private static HomeFeedData sInstance;

    private final AppPrefs mPrefs;
    private RecentHomeCards mRecent;

    private HomeFeedData(Context context) {
        mPrefs = AppPrefs.instance(context);
        rebuild();
    }

    public static HomeFeedData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HomeFeedData(context);
        }

        return sInstance;
    }

    /** The window to consult for every card Home wants to show, or null when the feature is off. */
    public RecentHomeCards recent() {
        return isFreshHomeEnabled() ? mRecent : null;
    }

    public boolean isFreshHomeEnabled() {
        return mPrefs.getBoolean(FRESH_HOME_ENABLED, true);
    }

    public void setFreshHomeEnabled(boolean value) {
        mPrefs.putBoolean(FRESH_HOME_ENABLED, value);
        rebuild();
    }

    /** Forget what Home has shown: the user's own reset, or an account change. */
    public void resetRecent() {
        mRecent.clear();
    }

    private void rebuild() {
        mRecent = new RecentHomeCards(new PrefsStore(), isFreshHomeEnabled());
    }

    /** Ids oldest first, one comma-joined line: video ids carry no comma. */
    private final class PrefsStore implements RecentHomeCards.Store {
        @Override
        public List<String> load() {
            String raw = mPrefs.getString(RECENT_HOME_CARDS, null);

            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }

            List<String> ids = new ArrayList<>();
            for (String id : raw.split(",")) {
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }

            return ids;
        }

        @Override
        public void save(List<String> ids) {
            if (ids == null || ids.isEmpty()) {
                mPrefs.putString(RECENT_HOME_CARDS, "");
                return;
            }

            StringBuilder sb = new StringBuilder(ids.size() * 12);
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(ids.get(i));
            }

            mPrefs.putString(RECENT_HOME_CARDS, sb.toString());
        }
    }
}
