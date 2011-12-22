/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.browse;

import android.provider.BaseColumns;
import com.android.email.providers.UIProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.android.email.R;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.LruCache;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Map;

/**
 * This is the view model for the conversation header. It includes all the
 * information needed to layout a conversation header view. Each view model is
 * associated with a conversation and is cached to improve the relayout time.
 */
public class BrowseItemViewModel {
    private static final int MAX_CACHE_SIZE = 100;

    boolean faded = false;
    int fontColor;
    @VisibleForTesting
    static LruCache<Pair<String, Long>, BrowseItemViewModel> sConversationHeaderMap
        = new LruCache<Pair<String, Long>, BrowseItemViewModel>(MAX_CACHE_SIZE);

    // The hashcode used to detect if the conversation has changed.
    private int mDataHashCode;
    private int mLayoutHashCode;

    // Star
    boolean starred;

    Bitmap starBitmap;

    // Date
    String dateText;
    Bitmap dateBackground;

    // Personal level
    Bitmap personalLevelBitmap;

    // Paperclip
    Bitmap paperclip;

    // Senders
    String sendersText;

    // A list of all the fragments that cover sendersText
    final ArrayList<SenderFragment> senderFragments;

    boolean hasAttachments;

    boolean hasDraftMessage;

    // Subject
    SpannableStringBuilder subjectText;

    StaticLayout subjectLayout;

    // View Width
    public int viewWidth;

    // Standard scaled dimen used to detect if the scale of text has changed.
    public int standardScaledDimen;

    public long dateMs;;

    public String subject;

    public String snippet;

    public String fromSnippetInstructions;

    public long conversationId;

    public long maxMessageId;

    public boolean checkboxVisible;


    /**
     * Returns the view model for a conversation. If the model doesn't exist for this conversation
     * null is returned. Note: this should only be called from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @return the view model for this conversation, or null
     */
    @VisibleForTesting
    static BrowseItemViewModel forConversationIdOrNull(
            String account, long conversationId) {
        final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
        synchronized(sConversationHeaderMap) {
            return sConversationHeaderMap.get(key);
        }
    }

    static BrowseItemViewModel forCursor(String account, Cursor cursor) {
        BrowseItemViewModel header = new BrowseItemViewModel();
        if (cursor != null) {
            int idCol = cursor.getColumnIndex(BaseColumns._ID);
            int subjectCol = cursor.getColumnIndex(UIProvider.ConversationColumns.SUBJECT);
            int snippetCol = cursor.getColumnIndex(UIProvider.ConversationColumns.SNIPPET);
            int dateCol = cursor.getColumnIndex(UIProvider.ConversationColumns.DATE_RECEIVED_MS);
            header.conversationId = cursor.getLong(idCol);
            header.dateMs = cursor.getLong(dateCol);
            header.subject = cursor.getString(subjectCol);
            header.snippet = cursor.getString(snippetCol);
            header.faded = false;
            header.checkboxVisible = true;
        }
        return header;
    }

    /**
     * Returns the view model for a conversation. If this is the first time
     * call, a new view model will be returned. Note: this should only be called
     * from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @param cursor the cursor to use in populating/ updating the model.
     * @return the view model for this conversation
     */
    static BrowseItemViewModel forConversationId(String account, long conversationId) {
        synchronized(sConversationHeaderMap) {
            BrowseItemViewModel header =
                    forConversationIdOrNull(account, conversationId);
            if (header == null) {
                final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
                header = new BrowseItemViewModel();
                sConversationHeaderMap.put(key, header);
            }
            return header;
        }
    }

    public BrowseItemViewModel() {
        senderFragments = Lists.newArrayList();
    }

    /**
     * Adds a sender fragment.
     *
     * @param start the start position of this fragment
     * @param end the start position of this fragment
     * @param style the style of this fragment
     * @param isFixed whether this fragment is fixed or not
     */
    void addSenderFragment(int start, int end, CharacterStyle style, boolean isFixed) {
        SenderFragment senderFragment = new SenderFragment(start, end, sendersText, style, isFixed);
        senderFragments.add(senderFragment);
    }

    /**
     * Clears all the current sender fragments.
     */
    void clearSenderFragments() {
        senderFragments.clear();
    }

    /**
     * Returns the hashcode to compare if the data in the header is valid.
     */
    private static int getHashCode(Context context, String dateText, String fromSnippetInstructions) {
        if (dateText == null) {
            return -1;
        }
        if (TextUtils.isEmpty(fromSnippetInstructions)) {
            fromSnippetInstructions = "fromSnippetInstructions";
        }
        return fromSnippetInstructions.hashCode() ^ dateText.hashCode();
    }

    /**
     * Returns the layout hashcode to compare to see if thet layout state has changed.
     */
    private int getLayoutHashCode() {
        return mDataHashCode ^ viewWidth ^ standardScaledDimen ^
                Boolean.valueOf(checkboxVisible).hashCode();
    }

    /**
     * Marks this header as having valid data and layout.
     */
    void validate(Context context) {
        mDataHashCode = getHashCode(context, dateText, fromSnippetInstructions);
        mLayoutHashCode = getLayoutHashCode();
    }

    /**
     * Returns if the data in this model is valid.
     */
    boolean isDataValid(Context context) {
        return mDataHashCode == getHashCode(context, dateText, fromSnippetInstructions);
    }

    /**
     * Returns if the layout in this model is valid.
     */
    boolean isLayoutValid(Context context) {
        return isDataValid(context) && mLayoutHashCode == getLayoutHashCode();
    }

    /**
     * Describes the style of a Senders fragment.
     */
    static class SenderFragment {
        // Coordinate where the text to be drawn.
        int x;
        int y;

        // Indices that determine which substring of mSendersText we are
        // displaying.
        int start;
        int end;

        // The style to apply to the TextPaint object.
        CharacterStyle style;

        // Width of the fragment.
        int width;

        // Ellipsized text.
        String ellipsizedText;

        // Whether the fragment is fixed or not.
        boolean isFixed;

        // Should the fragment be displayed or not.
        boolean shouldDisplay;

        SenderFragment(int start, int end, CharSequence sendersText, CharacterStyle style,
                boolean isFixed) {
            this.start = start;
            this.end = end;
            this.style = style;
            this.isFixed = isFixed;
        }
    }

    /**
     * Get conversation information to use for accessibility.
     */
    public CharSequence getContentDescription(Context context) {
       return context.getString(R.string.content_description, subject, snippet);
    }
}
