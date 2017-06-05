/*
 Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the
 License. A copy of the License is located at

 http://aws.amazon.com/apache2.0/

 or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 and limitations under the License.
 */

package com.mysampleapp.demo.bots;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobile.AWSMobileClient;
import com.amazonaws.mobile.bots.Conversation;
import com.amazonaws.mobile.bots.TextMessage;
import com.amazonaws.mobileconnectors.lex.interactionkit.InteractionClient;
import com.amazonaws.mobileconnectors.lex.interactionkit.Response;
import com.amazonaws.mobileconnectors.lex.interactionkit.continuations.LexServiceContinuation;
import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.InteractionListener;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lexrts.model.DialogState;
import com.mysampleapp.R;
import com.mysampleapp.demo.DemoFragmentBase;
import com.google.gson.Gson;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class ConversationalBotTextFragment extends DemoFragmentBase {
    final private String TAG = "ConversationalBotText";

    private EditText userTextInput;
    private InteractionClient lexInteractionClient;
    private boolean inConversation;
    private LexServiceContinuation convContinuation;
    private Context context;
    private TextView textMessage;
    private Bot currentBot;
    private static String ARGUMENT_DEMO_CONVERSATIONAL_BOT = "BOT";

    public static ConversationalBotTextFragment newInstance(Bot curentBot) {
        ConversationalBotTextFragment fragment = new ConversationalBotTextFragment();
        Bundle args = new Bundle();
        args.putSerializable(ConversationalBotTextFragment.ARGUMENT_DEMO_CONVERSATIONAL_BOT,
                curentBot);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_conversational_bot_text, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        context = getContext();
        final Bundle args = getArguments();
        currentBot = (Bot) args.getSerializable(ARGUMENT_DEMO_CONVERSATIONAL_BOT);
        if (currentBot==null) {
            BotFactory botFactory = BotFactory.Instance(getContext());
            List<Bot> bots =  botFactory.getBots();
            currentBot = bots.get(0);
        }
        userTextInput = (EditText) getActivity().findViewById(R.id.userInputEditText);

        startNewConversation();
        // Set text edit listener.
        userTextInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    textEntered();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentBot == null) {
            final Bundle args = getArguments();
            currentBot = (Bot) args.getSerializable(ARGUMENT_DEMO_CONVERSATIONAL_BOT);
        }
        initializeLexSDK();
    }

    /**
     * Initializes Lex client.
     */
    private void initializeLexSDK() {
        Log.d(TAG, "Lex Client");
        AWSCredentialsProvider credentialsProvider = AWSMobileClient.defaultMobileClient()
                .getIdentityManager().getUnderlyingProvider();

        // Create Lex interaction client.
        lexInteractionClient = new InteractionClient(context,
                credentialsProvider,
                Regions.fromName(currentBot.getRegion()),
                currentBot.getBotName(),
                currentBot.getBotAlias());
        lexInteractionClient.setInteractionListener(interactionListener);
    }

    /**
     * Read user text input.
     */
    private void textEntered() {
        String text = userTextInput.getText().toString();
        if (!inConversation) {
            Log.d(TAG, " -- New conversation started");
            startNewConversation();
            addMessage(new TextMessage(text, "tx", getCurrentTimeStamp()));
            lexInteractionClient.textInForTextOut(text, null);
            inConversation = true;
        } else {
            Log.d(TAG, " -- Responding with text: " + text);
            addMessage(new TextMessage(text, "tx", getCurrentTimeStamp()));
            convContinuation.continueWithTextInForTextOut(text);
        }
        clearTextInput();
    }

    /**
     * Pass user input to Lex client.
     * 
     * @param continuation
     */
    private void readUserText(LexServiceContinuation continuation) {
        convContinuation = continuation;
        inConversation = true;
    }

    /**
     * Clears the current conversation history and closes the current request.
     */
    private void startNewConversation() {
        Log.d(TAG, "Starting new conversation");
        Conversation.clear();
        inConversation = false;
        clearTextInput();
    }

    /**
     * Clear text input field.
     */
    private void clearTextInput() {
        userTextInput.setText("");
    }

    /**
     * Show the text message on the screen.
     * 
     * @param message
     */
    private void addMessage(TextMessage message) {
        Conversation.add(message);
        final ConversationalBotMessagesListAdapter listAdapter = new ConversationalBotMessagesListAdapter(
                getContext().getApplicationContext());
        final ListView messagesListView = (ListView) getActivity()
                .findViewById(R.id.conversationListView);
        messagesListView.setDivider(null);
        messagesListView.setAdapter(listAdapter);
        messagesListView.setSelection(listAdapter.getCount() - 1);
    }

    /**
     * Current time stamp.
     * 
     * @return
     */
    private String getCurrentTimeStamp() {
        return DateFormat.getDateTimeInstance().format(new Date());
    }

    final InteractionListener interactionListener = new InteractionListener() {
        @Override
        public void onReadyForFulfillment(Response response) {
            Log.d(TAG, "Transaction completed successfully");
            inConversation = false;
            final Gson gson = new Gson();
            addMessage(new TextMessage(gson.toJson(response.getSlots()), "rx", getCurrentTimeStamp()));
        }

        @Override
        public void promptUserToRespond(Response response,
                                        LexServiceContinuation continuation) {
            addMessage(new TextMessage(response.getTextResponse(), "rx", getCurrentTimeStamp()));
            readUserText(continuation);
        }

        @Override
        public void onInteractionError(Response response, Exception e) {
            if (response != null) {
                if (DialogState.Failed.toString().equals(response.getDialogState())) {
                    addMessage(new TextMessage(response.getTextResponse(), "rx",
                            getCurrentTimeStamp()));
                    inConversation = false;
                } else {
                    addMessage(new TextMessage("Please retry", "rx", getCurrentTimeStamp()));
                }
            } else {
                Log.e(TAG, "Interaction error", e);
                inConversation = false;
            }
        }
    };
}