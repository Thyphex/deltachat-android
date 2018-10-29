/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.dd.CircularProgressButton;
import com.soundcloud.android.crop.Crop;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter.OnRecipientDeletedListener;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.b44t.messenger.DcContact.DC_CONTACT_ID_SELF;

/**
 * Activity to create and update groups
 *
 * @author Jake McGinty
 */
public class GroupCreateActivity extends PassphraseRequiredActionBarActivity
                                 implements OnRecipientDeletedListener
{

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_ADDRESS_EXTRA = "group_recipient";
  public static final String GROUP_THREAD_EXTRA  = "group_thread";
  public static final String GROUP_CREATE_VERIFIED_EXTRA  = "group_create_verified";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  public static final  int AVATAR_SIZE  = 210;

  private ApplicationDcContext dcContext;

  private boolean      createVerified;
  private EditText     groupName;
  private ListView     lv;
  private ImageView    avatar;
  private TextView     creatingText;
  private Bitmap       avatarBmp;
  private CircularProgressButton verifyButton;
  private Integer editGroupChatId = null;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    dcContext = DcHelper.getContext(this);
    setContentView(R.layout.group_create_activity);
    //noinspection ConstantConditions
    createVerified = getIntent().getBooleanExtra(GROUP_CREATE_VERIFIED_EXTRA, false);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    initializeResources();
    initializeExistingGroup();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    updateViewState();
  }

  @SuppressWarnings("ConstantConditions")
  private void updateViewState() {
    avatar.setEnabled(true);
    groupName.setEnabled(true);

    String title;
    if(isEdit()) {
      title = getString(R.string.GroupCreateActivity_actionbar_edit_title);
    }
    else if(createVerified) {
      title = getString(R.string.GroupCreateActivity_actionbar_verified_title);
    }
    else {
      title = getString(R.string.GroupCreateActivity_actionbar_title);
    }
    getSupportActionBar().setTitle(title);
  }

  private void addSelectedContacts(@NonNull Recipient... recipients) {
    new AddMembersTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipients);
  }

  private static class AddMembersTask extends AsyncTask<Recipient,Void,List<AddMembersTask.Result>> {
    static class Result {
      Optional<Recipient> recipient;
      String              reason;

      public Result(@Nullable Recipient recipient, @Nullable String reason) {
        this.recipient = Optional.fromNullable(recipient);
        this.reason    = reason;
      }
    }

    private GroupCreateActivity activity;

    public AddMembersTask(@NonNull GroupCreateActivity activity) {
      this.activity      = activity;
    }

    @Override
    protected List<Result> doInBackground(Recipient... recipients) {
      final List<Result> results = new LinkedList<>();

      for (Recipient recipient : recipients) {
        results.add(new Result(recipient, null));
      }
      return results;
    }

    @Override
    protected void onPostExecute(List<Result> results) {
      if (activity.isFinishing()) return;
      for (Result result : results) {
        Recipient recipient = result.recipient.get();
        Address address = recipient.getAddress();
        if(address.isDcContact()) {
          activity.getAdapter().add(recipient, true);
        }
      }
      activity.updateViewState();
    }
  }

  private void initializeResources() {
    lv           = ViewUtil.findById(this, R.id.selected_contacts_list);
    avatar       = ViewUtil.findById(this, R.id.avatar);
    groupName    = ViewUtil.findById(this, R.id.group_name);
    creatingText = ViewUtil.findById(this, R.id.creating_group_text);
    verifyButton = ViewUtil.findById(this, R.id.verify_button);
    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this);
    adapter.setOnRecipientDeletedListener(this);
    lv.setAdapter(adapter);

    findViewById(R.id.add_member_button).setOnClickListener(new AddRecipientButtonListener());
    if (createVerified) {
      verifyButton.setOnClickListener(new ShowQrButtonListener());
      verifyButton.setVisibility(View.VISIBLE);
    }
    avatar.setImageDrawable(new ResourceContactPhoto(R.drawable.ic_group_white_24dp).asDrawable(this, ContactColors.UNKNOWN_COLOR.toConversationColor(this)));
    avatar.setOnClickListener(view -> Crop.pickImage(GroupCreateActivity.this));
  }

  private void initializeExistingGroup() {
    final Address groupAddress = getIntent().getParcelableExtra(GROUP_ADDRESS_EXTRA);
    if (groupAddress != null) {
      editGroupChatId = groupAddress.getDcChatId();
      getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      new FillExistingGroupInfoAsyncTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, editGroupChatId);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.group_create, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.menu_create_group:
        if (isEdit()) handleGroupUpdate();
        else                           handleGroupCreate();
        return true;
    }

    return false;
  }

  @Override
  public void onRecipientDeleted(Recipient recipient) {
    getAdapter().remove(recipient);
    updateViewState();
  }

  private void handleGroupCreate() {
    String groupName = getGroupName();
    if (showGroupNameEmptyToast(groupName)) return;

    int chatId = dcContext.createGroupChat(createVerified, groupName);

    Set<Recipient> recipients = getAdapter().getRecipients();
    for(Recipient recipient : recipients) {
      Address address = recipient.getAddress();
      if(address.isDcContact()) {
        int contactId = address.getDcContactId();
        dcContext.addContactToChat(chatId, contactId);
      }
    }
    setGroupAvatar(chatId);

    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, (long)chatId);
    startActivity(intent);
    finish();
  }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void setGroupAvatar(int chatId) {
        if(avatarBmp == null) {
          return;
        }
        String avatarPath = getFilesDir().getAbsolutePath() + "/chatAvatar_" + chatId + ".jpg";
        File oldImage = new File(avatarPath);
        if (oldImage.exists()) {
          oldImage.delete();
        }
        FileOutputStream outStream;
        try {
          outStream = new FileOutputStream(avatarPath);
          avatarBmp.compress(Bitmap.CompressFormat.JPEG, 85, outStream);
          dcContext.setChatProfileImage(chatId, avatarPath);
          Log.d("dboehrs", "setGroupAvatar: " + avatarPath);
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        }
    }

    private boolean showGroupNameEmptyToast(String groupName) {
    if(groupName == null) {
      Toast.makeText(this, getString(R.string.GroupCreateActivity_please_enter_group_name), Toast.LENGTH_LONG).show();
      return true;
    }
    return false;
  }

  private void handleGroupUpdate() {
    if(editGroupChatId == null) {
      return;
    }
    String groupName = getGroupName();
    if (showGroupNameEmptyToast(groupName)) {
      return;
    }
    dcContext.setChatName(editGroupChatId, groupName);
    updateGroupParticipants();

    setGroupAvatar(editGroupChatId);

    Intent intent = new Intent();
    Recipient recipient = dcContext.getRecipient(ApplicationDcContext.RECIPIENT_TYPE_CHAT, editGroupChatId);
    intent.putExtra(GroupCreateActivity.GROUP_ADDRESS_EXTRA, recipient.getAddress());
    setResult(RESULT_OK, intent);
    finish();
  }

  private void updateGroupParticipants() {
    SparseBooleanArray currentChatContactIds = new SparseBooleanArray();
    for(int chatContactId : dcContext.getChatContacts(editGroupChatId)) {
      currentChatContactIds.put(chatContactId, chatContactId == DC_CONTACT_ID_SELF);
    }

    Set<Recipient> recipients = getAdapter().getRecipients();
    for(Recipient recipient : recipients) {
      Address address = recipient.getAddress();
      if(address.isDcContact()) {
        int contactId = address.getDcContactId();
        if(currentChatContactIds.indexOfKey(contactId) < 0) {
          dcContext.addContactToChat(editGroupChatId, contactId);
        } else {
          currentChatContactIds.put(contactId, true);
        }
      }
    }
    for(int index = 0; index < currentChatContactIds.size(); index++) {
      if (!currentChatContactIds.valueAt(index)) {
        dcContext.removeContactFromChat(editGroupChatId, currentChatContactIds.keyAt(index));
      }
    }
  }

  private SelectedRecipientsAdapter getAdapter() {
    return (SelectedRecipientsAdapter)lv.getAdapter();
  }

  private @Nullable String getGroupName() {
    String ret = groupName.getText() != null ? groupName.getText().toString() : null;
    if(ret!=null) {
      ret = ret.trim();
      if(ret.isEmpty()) {
        ret = null;
      }
    }
    return ret;
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, final Intent data) {
    super.onActivityResult(reqCode, resultCode, data);
    Uri outputFile = Uri.fromFile(new File(getCacheDir(), "cropped"));

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<String> selected = data.getStringArrayListExtra("contacts");

        for (String contact : selected) {
          Address   address   = Address.fromExternal(this, contact);
          Recipient recipient = Recipient.from(this, address, false);

          addSelectedContacts(recipient);
        }
        break;

      case Crop.REQUEST_PICK:
        new Crop(data.getData()).output(outputFile).asSquare().start(this);
        break;
      case Crop.REQUEST_CROP:
        GlideApp.with(this)
                .asBitmap()
                .load(Crop.getOutput(data))
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .centerCrop()
                .override(AVATAR_SIZE, AVATAR_SIZE)
                .into(new SimpleTarget<Bitmap>() {
                  @Override
                  public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                    setAvatar(Crop.getOutput(data), resource);
                  }
                });
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, ContactMultiSelectionActivity.class);
      intent.putExtra(ContactSelectionListFragment.SELECT_VERIFIED_EXTRA, createVerified);
      if (isEdit()) {
        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_PUSH);
      } else {
        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_PUSH | DisplayMode.FLAG_SMS);
      }
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private class ShowQrButtonListener implements View.OnClickListener {

    @Override
    public void onClick(View view) {
        Intent qrIntent = new Intent(GroupCreateActivity.this, QrShowActivity.class);
        startActivity(qrIntent);
    }
  }

  private static class FillExistingGroupInfoAsyncTask extends ProgressDialogAsyncTask<Integer,Void,Recipient> {

    GroupCreateActivity activity;

    FillExistingGroupInfoAsyncTask(GroupCreateActivity activity) {
      super(activity,
            R.string.GroupCreateActivity_loading_group_details,
            R.string.please_wait);
      this.activity = activity;
    }

    @Override
    protected Recipient doInBackground(Integer... recipientIds) {
      Integer recipientsId = recipientIds[0];
      return activity.dcContext.getRecipient(ApplicationDcContext.RECIPIENT_TYPE_CHAT, recipientsId);
    }

    @Override
    protected void onPostExecute(Recipient recipient) {
      super.onPostExecute(recipient);
      activity.fllExistingGroup(recipient);
    }
  }

  private void fllExistingGroup(Recipient recipient) {
    List<Recipient> participants = recipient.getParticipants();
    if (!isFinishing()) {
      Recipient ownAddress = null;
      for(Recipient participant : participants) {
        if(participant.getAddress().getDcContactId() == DC_CONTACT_ID_SELF) {
          ownAddress = participant;
        } else {
          addSelectedContacts(participant);
        }
      }
      if (ownAddress != null) {
        participants.remove(ownAddress);
      }
      groupName.setText(recipient.getName());
      SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this, participants);
      adapter.setOnRecipientDeletedListener(this);
      lv.setAdapter(adapter);
      updateViewState();
    }
  }

  private <T> void setAvatar(T model, Bitmap bitmap) {
    avatarBmp = bitmap;
    GlideApp.with(this)
            .load(model)
            .circleCrop()
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(avatar);
  }

  private boolean isEdit() {
    return editGroupChatId != null;
  }

}
