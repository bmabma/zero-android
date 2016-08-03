package ekylibre.zero;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import ekylibre.zero.service.ConnectionManagerService;
import ekylibre.zero.util.AccountTool;

/**************************************
 * Created by pierre on 7/12/16.      *
 * ekylibre.zero for zero-android     *
 *************************************/


/*
** The home menu for the app
** There is a slide menu if you slide left including :
** - Information activity
** - Some management activity
**
** You can access all actions activity from the floating button
*/
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener
{

    private Account         mAccount;
    private TextView        mNav_account;
    private TextView        mNav_instance;
    private NavigationView  mNavigationView;
    private Toolbar         mToolbar;
    private boolean         firstPass;
    private final String    TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        firstPass = true;
        if (!AccountTool.isAnyAccountExist(this))
            AccountTool.askForAccount(this, this);
        mAccount = AccountTool.getCurrentAccount(MainActivity.this);
        setToolbar();
/*
        setFloatingActBtn();
*/
        setDrawerLayout();
        setTodolist();
        startConnectionManager();
        View headerLayout = mNavigationView.inflateHeaderView(R.layout.nav_header_main);
        mNav_account = (TextView)headerLayout.findViewById(R.id.nav_accountName);
        mNav_instance = (TextView)headerLayout.findViewById(R.id.nav_farmURL);
    }

    /*
    ** Callback call when the activity is visible / revisible
    ** Use to set / reset the header of the drawer layout
    */
    @Override
    public void onStart()
    {
        super.onStart();
        if (!AccountTool.isAnyAccountExist(this))
            AccountTool.askForAccount(this, this);
        mAccount = AccountTool.getCurrentAccount(MainActivity.this);
        setAccountName(mNavigationView);
        firstPass = false;
    }

    /*
    ** Start service which auto sync when internet is detected
    */
    private void    startConnectionManager()
    {
        Intent connectIntent = new Intent(MainActivity.this, ConnectionManagerService.class);
        connectIntent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mAccount.name);
        startService(connectIntent);
    }

    /*
    ** Setting the navigation view on drawer layout
    ** This is the slide menu on the left
    */
    private void    setDrawerLayout()
    {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        mNavigationView = navigationView;
    }

    /*
    ** Setting the floating button which is use to access to
    ** different type of action buttons
    */
/*    private void    setFloatingActBtn()
    {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }*/

    /*
    ** Set toolbar which is the new version of th action bar
    */
    private void    setToolbar()
    {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mToolbar = toolbar;
    }

    /*
    ** Get and set name and instance of the actual account setting appropriate private variable
    */
    private void    setAccountName(NavigationView navigationView)
    {
        mAccount = AccountTool.getCurrentAccount(this);
        mNav_instance.setText(AccountTool.getAccountInstance(mAccount, this));
        mNav_account.setText(AccountTool.getAccountName(mAccount, this));
    }

    /*
    ** The main content of the activity which is set on TodoListActivity
    ** This list will sync your phone and Ekylibre calendar to show all
    ** tasks of the current day
    */
    private void    setTodolist()
    {
        TodoListActivity todo = new TodoListActivity(MainActivity.this,
                (ListView)findViewById(R.id.listView), (TextView)findViewById(R.id.todayDate));
    }

    /*
    ** Close slide menu on back press
    */
    @Override
    public void onBackPressed()
    {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
        {
            drawer.closeDrawer(GravityCompat.START);
        }
        else
        {
            super.onBackPressed();
        }
    }

    /*
    ** Set toolbar options
    */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /*
    ** Actions on toolbar items
    ** Items are identified by their view id
    */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int     id = item.getItemId();
        Intent  intent;

        if (id == R.id.action_settings)
        {
            intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return (true);
        }
        return super.onOptionsItemSelected(item);
    }

    /*
    ** Actions on slide menu
    ** Items are identified by their view id on menu file
    */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == R.id.nav_tracking)
        {
            Intent intent = new Intent(this, TrackingActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.nav_issue)
        {
            Intent intent = new Intent(this, IssueActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.nav_counting)
        {
            Intent intent = new Intent(this, PlantCountingActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.nav_settings)
        {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.nav_account)
        {
            Intent intent = new Intent(this, AccountManagerActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return (true);
    }

    /*
    ** onClick method which is call when click on header of drawer layout is detected
    ** actually redirecting to the account manager
    */
    public void launchAccountManagerActivity(View v)
    {
        Intent intent = new Intent(this, AccountManagerActivity.class);
        startActivity(intent);
    }
}