package com.hasura.todo.Todo.ui.online

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.hasura.todo.GetOnlineUsersSubscription
import com.hasura.todo.Todo.Login
import com.hasura.todo.Todo.R
import com.hasura.todo.Todo.network.Network
import com.hasura.todo.UpdateLastSeenMutation
import kotlinx.android.synthetic.main.fragment_online.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.fixedRateTimer

class OnlineFragment : Fragment() {

    private lateinit var updateLastSeenTimer: Timer
    private lateinit var lastSeenMutation: UpdateLastSeenMutation
    private lateinit var onlineUsersSubscriptionQuery: GetOnlineUsersSubscription
    private var onlineUsersSubscription: ApolloSubscriptionCall<GetOnlineUsersSubscription.Data>? = null

    private lateinit var listView: ListView
    private lateinit var onlineUserCount: TextView

    private var listItems = mutableListOf<String>(

    )

    private lateinit var adapter: ArrayAdapter<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscribeOnlineUsers()
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_online, container, false)
        onlineUserCount = root.text_online
        setOnlineUsersCount()

        listView = root.findViewById(R.id.list_online)

        adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, listItems)
        listView.adapter = adapter

        updateLastSeenTimer = fixedRateTimer("timer",false,0,5000){
            activity?.runOnUiThread {
                updateLastSeen()
            }
        }

        return root
    }

    private fun updateLastSeen(){
        val ISO8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ")

        val cal: Calendar = Calendar.getInstance()
        val date : Date = cal.time
        val formattedDate : String = ISO8601.format(date)

        // Init Query
        lastSeenMutation = UpdateLastSeenMutation.builder().now(formattedDate).build()

        Network.apolloClient.mutate(lastSeenMutation).enqueue(object: ApolloCall.Callback<UpdateLastSeenMutation.Data>(){
            override fun onFailure(e: ApolloException) {
                Log.d("Online Users", e.toString())
            }

            override fun onResponse(response: Response<UpdateLastSeenMutation.Data>) {
                Log.d("Online Users", "Successfully Updated Last Seen :  $response")
            }
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.logout, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // handle item selection
        when (item.itemId) {
            R.id.logout ->
                logout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setOnlineUsersCount(){
        onlineUserCount.text = "Total Online Users: ${listItems.size}"
    }

    private fun logout() {
        val intent = Intent(requireContext(), Login::class.java)
        intent.putExtra(Login.KEY_CLEAR_CREDENTIALS, true)
        startActivity(intent)
        activity?.finish()
    }

    override fun onPause() {
        super.onPause()
        onlineUsersSubscription?.cancel()
        updateLastSeenTimer.cancel()
    }

    private fun subscribeOnlineUsers() {
        // Init Query
        onlineUsersSubscriptionQuery = GetOnlineUsersSubscription.builder().build()
        onlineUsersSubscription = Network.apolloClient.subscribe(onlineUsersSubscriptionQuery)
        onlineUsersSubscription?.execute(object: ApolloSubscriptionCall.Callback<GetOnlineUsersSubscription.Data> {
            override fun onFailure(e: ApolloException) {
                Log.d("Online Users", e.localizedMessage)
            }
            override fun onResponse(response: Response<GetOnlineUsersSubscription.Data>) {
                Log.d("Subs", response.data().toString())
                val userList = mutableListOf(response.data()!!).flatMap {
                        data -> data.online_users().map{
                        data -> data.user()?.name()
                }
                }
                listItems.clear()
                listItems.addAll(userList.toMutableList().filter { t -> t!= null }.map { t -> t!! }.toMutableList())
                activity?.runOnUiThread {
                    setOnlineUsersCount()
                    adapter.notifyDataSetChanged()
                }
            }
            override fun onConnected() {
                Log.d("Online Users", "Connected to WS" )
            }
            override fun onTerminated() {
                Log.d("Online Users", "Dis-connected from WS" )
            }
            override fun onCompleted() {
            }
        })
    }

}