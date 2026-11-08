package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import com.liskovsoft.smartyoutubetv2.common.prefs.MobileCardsData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * NEWTUBE(card-density): the phone card's text block, measured rather than eyeballed.
 *
 * <p>Two claims from the change: the title now runs the full card width (it used to start behind
 * the channel avatar) while still stopping at two lines, and the avatar moved down onto the middle
 * line, below the title. The compact variant then tightens margins, avatar, middle line and the
 * menu button without touching either claim. Geometry is asserted at a 411dp-wide phone, which is
 * what the owner's device reports.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VideoCardDensityTest {

    /** 411dp at xxhdpi (density 3) — the phone this was written for. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void titleRunsTheFullWidthAndTheAvatarMovesToTheMiddleLine() {
        Context context = context();
        View card = inflateCard(context);

        int widthPx = Math.round(411 * context.getResources().getDisplayMetrics().density);
        measure(card, widthPx);

        TextView title = card.findViewById(R.id.video_title);
        TextView meta = card.findViewById(R.id.video_meta);
        ImageView avatar = card.findViewById(R.id.video_channel_avatar);
        View overflow = card.findViewById(R.id.video_overflow);

        // Two lines, YouTube's size, not 16sp.
        assertEquals("title must stay capped at two lines", 2, title.getMaxLines());
        assertEquals(dp(context, 15), title.getTextSize(), 1f);

        // The title starts at the card's own edge instead of 52dp in.
        assertTrue("title should start at the card edge, was " + title.getLeft(),
                title.getLeft() <= dp(context, 6));
        // ...and runs to the menu button, so it owns nearly the whole card.
        assertTrue("title should own the card width: " + title.getWidth() + " of " + card.getWidth(),
                title.getWidth() >= card.getWidth() - dp(context, 60));
        assertTrue("title must end before the menu button", title.getRight() <= overflow.getLeft() + 1);

        // The avatar is on the middle line now: its row sits below the title, and inside the row
        // the avatar is centred with the meta text. (The avatar's own getTop() is relative to the
        // row, so the row is what has to be compared with the title.)
        View metaRow = card.findViewById(R.id.video_meta_row);
        assertTrue("the middle line should sit below the title (row.top=" + metaRow.getTop()
                + ", title.bottom=" + title.getBottom() + ")", metaRow.getTop() >= title.getBottom());
        int avatarCentre = (avatar.getTop() + avatar.getBottom()) / 2;
        int metaCentre = (meta.getTop() + meta.getBottom()) / 2;
        assertTrue("avatar should share the middle line's centre (" + avatarCentre + " vs " + metaCentre + ")",
                Math.abs(avatarCentre - metaCentre) <= dp(context, 2));
        assertTrue("meta should start after the avatar", meta.getLeft() >= avatar.getRight());
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void compactTightensTheCardButKeepsTheTwoLineTitle() {
        Context context = context();
        View card = inflateCard(context);

        int widthPx = Math.round(411 * context.getResources().getDisplayMetrics().density);
        VideoCardAdapter.applyCardDensity(card, true);
        measure(card, widthPx);

        TextView title = card.findViewById(R.id.video_title);
        TextView meta = card.findViewById(R.id.video_meta);
        ImageView avatar = card.findViewById(R.id.video_channel_avatar);
        View overflow = card.findViewById(R.id.video_overflow);

        assertEquals("title keeps two lines in compact", 2, title.getMaxLines());
        assertEquals(dp(context, 15), title.getTextSize(), 1f);
        assertEquals("compact avatar is 32dp", dp(context, 32), avatar.getWidth());
        assertEquals(dp(context, 32), avatar.getHeight());
        assertEquals("compact middle line is 12sp", sp(context, 12), meta.getTextSize(), 1f);
        assertEquals("compact menu button is 40dp", dp(context, 40), overflow.getWidth());
        assertEquals(dp(context, 40), overflow.getHeight());

        ViewGroup.MarginLayoutParams cardParams = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        assertEquals("compact card margin is 6dp", dp(context, 6), cardParams.leftMargin);
        ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) title.getLayoutParams();
        assertEquals("compact title top margin is 8dp", dp(context, 8), titleParams.topMargin);
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void compactIsOffByDefaultAndPersists() {
        Context context = context();
        assertTrue("compact cards must default to off", !MobileCardsData.isCompactCards(context));
        MobileCardsData.setCompactCards(context, true);
        assertTrue("the switch must stick", MobileCardsData.isCompactCards(context));
        MobileCardsData.setCompactCards(context, false);
        assertTrue(!MobileCardsData.isCompactCards(context));
    }

    private static Context context() {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
    }

    private static View inflateCard(Context context) {
        FrameLayout parent = new FrameLayout(context);
        View card = LayoutInflater.from(context).inflate(R.layout.item_video_card, parent, false);
        // The avatar ships GONE on screens without a channel; a GONE view is not laid out, and
        // this test is about where it lands.
        card.findViewById(R.id.video_channel_avatar).setVisibility(View.VISIBLE);
        return card;
    }

    private static void measure(View card, int widthPx) {
        card.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static float sp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }
}
