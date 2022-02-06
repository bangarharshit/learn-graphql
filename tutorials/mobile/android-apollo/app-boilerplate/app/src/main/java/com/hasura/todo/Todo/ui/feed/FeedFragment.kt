package com.hasura.todo.Todo.ui.feed

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.hasura.todo.AddTodoMutation
import com.hasura.todo.GetInitialPublicTodosQuery
import com.hasura.todo.NotifyNewPublicTodosSubscription
import com.hasura.todo.Todo.R
import com.hasura.todo.Todo.network.Network
import com.hasura.todo.UpdateLastSeenMutation
import kotlinx.android.synthetic.main.feed_notifiction.view.*
import kotlinx.android.synthetic.main.fragment_feed.view.*
import kotlinx.android.synthetic.main.load_more.view.*
import org.jetbrains.annotations.NotNull

class FeedFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var notificationCountText: TextView
    private lateinit var initialPublicTodosQuery: GetInitialPublicTodosQuery


    private lateinit var newPublicTodosSubscriptionQuery: NotifyNewPublicTodosSubscription
    private var newpublicTodoSubscription: ApolloSubscriptionCall<NotifyNewPublicTodosSubscription.Data>? = null

    private val listItems = mutableListOf<String>()
    private val notificationCount: MutableList<Int> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscribeNewPublicTodo()
    }

    // Disable Subscriptions
    override fun onPause() {
        super.onPause()
        newpublicTodoSubscription?.cancel()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // Initial public todos
        getInitialPublicTodosQuery()
    }

    private fun getInitialPublicTodosQuery(){
        // Init Query
        initialPublicTodosQuery = GetInitialPublicTodosQuery.builder().build()

        // Apollo runs query on background thread
        Network.apolloClient
            .query(initialPublicTodosQuery)
            .responseFetcher(ApolloResponseFetchers.NETWORK_ONLY)
            .enqueue(object : ApolloCall.Callback<GetInitialPublicTodosQuery.Data>() {
                override fun onFailure(error: ApolloException) {
                    Log.d("Public Feed", error.toString() )
                }

                override fun onResponse(@NotNull response: Response<GetInitialPublicTodosQuery.Data>) {
                    // Changing UI must be on UI thread
                    val publicTodoList = mutableListOf(response.data()!!).flatMap {
                            data -> data.todos().map{
                            data -> "@${data.user().name()} - ${data.title()}"
                    }
                    }
                    listItems.clear()
                    listItems.addAll(publicTodoList.toMutableList())

                }
            })
    }


    private fun subscribeNewPublicTodo(){
        // Init Query
        newPublicTodosSubscriptionQuery = NotifyNewPublicTodosSubscription.builder().build()

        newpublicTodoSubscription = Network.apolloClient
            .subscribe(newPublicTodosSubscriptionQuery)

        newpublicTodoSubscription?.execute(object: ApolloSubscriptionCall
        .Callback<NotifyNewPublicTodosSubscription.Data> {
            override fun onFailure(e: ApolloException) {
                Log.d("Public Feed", e.toString())
            }

            override fun onResponse(response: Response<NotifyNewPublicTodosSubscription.Data>) {
                Log.d("Public Feed Subs", response.data().toString())

                val notifId: Int = mutableListOf(response.data()!!).flatMap {
                        data -> data.todos()
                }.first().id()
                listItems.add(mutableListOf(response.data()!!).flatMap {
                        data -> data.todos()
                }.first().title())
                    notificationCount.add(notifId)
                activity?.runOnUiThread {
                    setNotificationCountText()
                    adapter.notifyDataSetChanged()
                }

            }

            override fun onConnected() {
                Log.d("Public Feed", "Connected to WS" )
            }

            override fun onTerminated() {
                Log.d("Public Feeds", "Dis-connected from WS" )
            }

            override fun onCompleted() {
            }

        })
    }

    private lateinit var adapter: ArrayAdapter<String>
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_feed, container, false)
        val input = root.title_text
        input.setOnEditorActionListener { v, actionId, event ->
            if ((event != null && (event.keyCode == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_NEXT)) {

                // Add Todo
                if ( input.editableText.toString() != ""){
                    addPublicTodo(input.editableText.toString())
                }

                // Dismiss Keyboard
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view?.windowToken, 0)

                // Clear Input
                input.setText("")
            }
            false
        }
        val footer = inflater.inflate(R.layout.load_more, container, false)
        val notificationView = inflater.inflate(R.layout.feed_notifiction, container, false)
        val notificationButton = notificationView.viewNotification
        notificationCountText = notificationView.textNotification
        setNotificationCountText()

        notificationButton.setOnClickListener{ v -> viewNotificationFeed() }
        val loadMore: Button = footer.loadMore
        loadMore.setOnClickListener{ v -> loadMoreItems() }

        listView = root.list_feed
        listView.addHeaderView(notificationView)
        listView.addFooterView(footer)


        adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, listItems)
        listView.adapter = adapter

        return root
    }

    private fun addPublicTodo(title: String){
        val addTodoMutation = AddTodoMutation.builder().todo(title).isPublic(true).build()
        Network.apolloClient.mutate(addTodoMutation).enqueue(object: ApolloCall.Callback<AddTodoMutation.Data>(){
            override fun onFailure(e: ApolloException) {
                Log.d("Online Users", e.toString())
            }

            override fun onResponse(response: Response<AddTodoMutation.Data>) {
                Log.d("Online Users", "Successfully Updated Last Seen :  $response")
            }
        })
    }

    private fun loadMoreItems(){
        // TODO : More items to load
    }

    private fun viewNotificationFeed(){
        // TODO : More items to load
    }

    private fun setNotificationCountText(){
        notificationCountText.text = "${notificationCount.size} New tasks available"
    }
}