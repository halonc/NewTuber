package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * NEWTUBE(fresh-home): the cards Home has already shown, so a refetch can drop them.
 *
 * <p>YouTube answers the Home browse request with mostly the same cards again: measured 2026-11-05
 * against a fresh anonymous visitor (no history, so a brand new identity each call) the topic feeds
 * that back Home up repeated 73-94% of their cards - music 19/23, news 11/15, gaming 47/50. A pull to
 * refresh therefore repaints nearly the same screen: on the third refresh of one session only 1 of the
 * first 12 cards was new, on the fourth none. The server holds the recommendation pool; what the app
 * can do is not show the same card twice in a row, which is the part of the complaint that is fixable
 * here. See issue #27 (closed as not planned): the refetch itself already happens.</p>
 *
 * <p>A card is dropped while it is in the window {@link #SUPPRESS_MS}; after that it may come back, so
 * a long-running subscription is not silenced forever. {@link #MAX_IDS} bounds memory and the store.</p>
 *
 * <p>Suppression is capped per fetch at {@link #MAX_SUPPRESSED_PER_REFRESH}: when YouTube answers the
 * same page over and over, dropping all of it would leave an empty Home, which is worse than a stale
 * one. Past the cap the rest of the page is shown as it is. The counter resets in
 * {@link #beginRefresh()}, which the presenter calls when a Home fetch starts.</p>
 *
 * <p>Pure state (no Android types) so the window and the cap can be tested on their own; the app hands
 * it a store backed by the app's own preferences file.</p>
 */
public final class RecentHomeCards {
    /** Cards remembered at most. About ten screens of Home, at ~150 cards a fetch. */
    public static final int MAX_IDS = 1_500;
    /** A card may appear again once it has been gone this long. */
    public static final long SUPPRESS_MS = 24 * 60 * 60 * 1_000L;
    /** Cards one fetch may drop before the rest of that page is shown anyway. */
    public static final int MAX_SUPPRESSED_PER_REFRESH = 40;

    /** The app's persistence, kept out of this class so it stays testable. */
    public interface Store {
        /** Ids oldest first, or an empty list. */
        List<String> load();

        void save(List<String> ids);
    }

    private final Deque<Entry> mEntries = new LinkedList<>();
    private final Store mStore;
    private final boolean mEnabled;
    /** Test seam. */
    private long mNowMs = System.currentTimeMillis();
    private int mSuppressedThisRefresh;
    private boolean mDirty;

    private static final class Entry {
        final String id;
        long shownMs;

        Entry(String id, long shownMs) {
            this.id = id;
            this.shownMs = shownMs;
        }
    }

    public RecentHomeCards(Store store, boolean enabled) {
        mStore = store;
        mEnabled = enabled;
        if (mEnabled && mStore != null) {
            List<String> ids = mStore.load();
            if (ids != null) {
                long ageMs = Math.max(0, System.currentTimeMillis() - mNowMs);
                for (String id : ids) {
                    if (id == null || id.isEmpty()) {
                        continue;
                    }
                    // Persisted ids carry no timestamp of their own: count them from now, and let the
                    // window age them out on their new visit rather than resurrect a stale screen.
                    push(new Entry(id, mNowMs - Math.min(ageMs, SUPPRESS_MS)));
                }
            }
        }
    }

    /** A Home fetch is starting: the cap below is counted anew for it. */
    public void beginRefresh() {
        mSuppressedThisRefresh = 0;
    }

    /** Called for every card Home is about to show. */
    public boolean shouldDrop(String videoId) {
        if (!mEnabled || videoId == null || videoId.isEmpty()) {
            return false;
        }

        Entry entry = find(videoId);
        if (entry == null) {
            return false;
        }

        if (mNowMs - entry.shownMs >= SUPPRESS_MS) {
            mEntries.remove(entry); // out of the window: the card is fair game again
            mDirty = true;
            return false;
        }

        if (mSuppressedThisRefresh >= MAX_SUPPRESSED_PER_REFRESH) {
            return false; // empty Home is worse than a repeated one: show the rest of the page
        }

        mSuppressedThisRefresh++;
        return true;
    }

    /** The card was shown. A card shown again moves to the back of the window. */
    public void record(String videoId) {
        if (!mEnabled || videoId == null || videoId.isEmpty()) {
            return;
        }

        Entry entry = find(videoId);
        if (entry != null) {
            entry.shownMs = mNowMs;
            touch(entry);
            mDirty = true;
            return;
        }

        push(new Entry(videoId, mNowMs));
        mDirty = true;
    }

    /** Cards shown again should sit at the recent end of the window. */
    private void touch(Entry entry) {
        mEntries.remove(entry);
        mEntries.addLast(entry);
    }

    private void push(Entry entry) {
        mEntries.addLast(entry);
        while (mEntries.size() > MAX_IDS) {
            mEntries.pollFirst();
        }
    }

    private Entry find(String videoId) {
        for (Entry entry : mEntries) {
            if (entry.id.equals(videoId)) {
                return entry;
            }
        }
        return null;
    }

    /** Forget everything shown so far: sign out, account change, or the user's own reset. */
    public void clear() {
        mEntries.clear();
        mSuppressedThisRefresh = 0;
        if (mStore != null) {
            mStore.save(new ArrayList<>());
        }
        mDirty = false;
    }

    /** Write the window out if it changed. Cheap enough to call after every page. */
    public void flush() {
        if (!mEnabled || !mDirty || mStore == null) {
            return;
        }
        List<String> ids = new ArrayList<>(mEntries.size());
        for (Entry entry : mEntries) {
            ids.add(entry.id);
        }
        mStore.save(ids);
        mDirty = false;
    }

    public int size() {
        return mEntries.size();
    }

    public int suppressedThisRefresh() {
        return mSuppressedThisRefresh;
    }

    /** Test seam: move the clock. */
    public void setNowMs(long nowMs) {
        mNowMs = nowMs;
    }

    /** Ids oldest first, for the store and for tests. */
    public List<String> ids() {
        List<String> ids = new ArrayList<>(mEntries.size());
        for (Entry entry : mEntries) {
            ids.add(entry.id);
        }
        return ids;
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new HashMap<>();
        for (Entry entry : mEntries) {
            out.put(entry.id, entry.shownMs);
        }
        return out;
    }
}
