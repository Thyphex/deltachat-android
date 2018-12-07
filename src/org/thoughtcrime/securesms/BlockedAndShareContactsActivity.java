package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcContactsLoader;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Objects;

public class BlockedAndShareContactsActivity extends PassphraseRequiredActionBarActivity {

  public static final String SHOW_ONLY_BLOCKED_EXTRA = "show_only_blocked";
  public static final String SHARE_CONTACT_NAME_EXTRA = "share_contact_Name";
  public static final String SHARE_CONTACT_MAIL_EXTRA = "share_contact_mail";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    boolean showOnlyBlocked = getIntent().getBooleanExtra(SHOW_ONLY_BLOCKED_EXTRA, false);
    getSupportActionBar().setTitle(showOnlyBlocked ? R.string.BlockedContactsActivity_blocked_contacts : R.string.ContactsCursorLoader_contacts);
    initFragment(android.R.id.content, new BlockedAndShareContactsFragment(), null, getIntent().getExtras());
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  public static class BlockedAndShareContactsFragment
          extends Fragment
          implements LoaderManager.LoaderCallbacks<DcContactsLoader.Ret>,
          DcEventCenter.DcEventDelegate, ContactSelectionListAdapter.ItemClickListener {


    private RecyclerView recyclerView;

    private SwipeRefreshLayout swipeRefreshLayout;

    private boolean showOnlyBlocked;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
      View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);
      recyclerView  = ViewUtil.findById(view, R.id.recycler_view);
      swipeRefreshLayout  = ViewUtil.findById(view, R.id.swipe_refresh);
      recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
      return view;
    }

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);
      showOnlyBlocked = getArguments().getBoolean(SHOW_ONLY_BLOCKED_EXTRA, false);
      getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
      super.onActivityCreated(bundle);
      initializeAdapter();
    }

    private void initializeAdapter() {
      ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(),
              GlideApp.with(this),
              this,
              false,
              false);
      recyclerView.setAdapter(adapter);
      swipeRefreshLayout.setRefreshing(false);
      swipeRefreshLayout.setEnabled(false);
    }

    @Override
    public Loader<DcContactsLoader.Ret> onCreateLoader(int id, Bundle args) {
      return new DcContactsLoader(getActivity(), showOnlyBlocked ? -1 : DcContext.DC_GCL_ADD_SELF, null, false, showOnlyBlocked);
    }

    @Override
    public void onLoadFinished(Loader<DcContactsLoader.Ret> loader, DcContactsLoader.Ret data) {
      getContactSelectionListAdapter().changeData(data);
    }

    @Override
    public void onLoaderReset(Loader<DcContactsLoader.Ret> loader) {
      getContactSelectionListAdapter().changeData(null);
    }

    @Override
    public void handleEvent(int eventId, Object data1, Object data2) {
      if (eventId==DcContext.DC_EVENT_CONTACTS_CHANGED) {
        restartLoader();
      }
    }

    private void restartLoader() {
      getLoaderManager().restartLoader(0, null, BlockedAndShareContactsFragment.this);
    }

    private ContactSelectionListAdapter getContactSelectionListAdapter() {
      return (ContactSelectionListAdapter) recyclerView.getAdapter();
    }

    @Override
    public void onItemClick(ContactSelectionListItem item, boolean handleActionMode) {
      if(showOnlyBlocked) {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
                .setMessage(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.RecipientPreferenceActivity_unblock, (dialog, which) -> unblockContact(item.getContactId())).show();
      } else {
        shareContact(item.getName(), item.getNumber());
      }
    }

    private void unblockContact(int contactId) {
      ApplicationDcContext dcContext = DcHelper.getContext(getContext());
      dcContext.blockContact(contactId, 0);
      restartLoader();
    }

    private void shareContact(String name, String mail) {
      Intent intent = new Intent();
      intent.putExtra(BlockedAndShareContactsActivity.SHARE_CONTACT_NAME_EXTRA, name);
      intent.putExtra(BlockedAndShareContactsActivity.SHARE_CONTACT_MAIL_EXTRA, mail);
      FragmentActivity activity = Objects.requireNonNull(getActivity());
      activity.setResult(RESULT_OK, intent);
      activity.finish();
    }

    @Override
    public void onItemLongClick(ContactSelectionListItem view) {
      // Not needed
    }
  }

}