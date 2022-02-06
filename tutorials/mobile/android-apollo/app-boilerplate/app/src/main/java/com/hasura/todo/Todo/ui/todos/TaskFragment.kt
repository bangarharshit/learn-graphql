package com.hasura.todo.Todo.ui.todos

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.hasura.todo.*
import com.hasura.todo.Todo.R
import com.hasura.todo.Todo.network.Network
import kotlinx.android.synthetic.main.task_todos.*
import kotlinx.android.synthetic.main.task_todos.view.*
import org.jetbrains.annotations.NotNull
import java.util.*

private const val COMPLETE_STATUS = "status"

class TaskFragment : Fragment(), TaskAdapter.TaskItemClickListener {
    private lateinit var getMyTodosQuery: GetMyTodosQuery
    private var completeStatus: String? = null

    interface FragmentListener {
        fun notifyDataSetChanged()
    }
    var filteredListItems: MutableList<Task> = listItems.map { it -> Task(it.id(), it.title(), it.is_completed) }.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            completeStatus = it.getString(COMPLETE_STATUS)
        }

        // Get Initial Todos from cloud
        getMyTodoQueryCloud()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.task_todos, container, false)
        val removeAllCompleted: Button = root.removeAllCompleted
        removeAllCompleted.setOnClickListener {removeAllCompleted()}
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        taskRecyclerView.layoutManager = LinearLayoutManager(activity)
        updateTabs()
    }

    fun refreshData() {
        updateTabs()
    }

    private fun updateTabs() {
        filteredListItems = listItems.map { it -> Task(it.id(), it.title(), it.is_completed) }.toMutableList()
        when (completeStatus) {
            ALL -> getFilteredData(filteredListItems)
            ACTIVE -> getFilteredData(filteredListItems.filter { task -> !task.getCompleteStatus() } as MutableList<Task>)
            COMPLETED -> getFilteredData(filteredListItems.filter { task -> task.getCompleteStatus() } as MutableList<Task>)
        }
        taskRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun getFilteredData(list: MutableList<Task>) {
        if (list.isNotEmpty()) {
            emptyMessageTextView.visibility = View.INVISIBLE
            taskRecyclerView.visibility = View.VISIBLE
            val taskAdapter = TaskAdapter(list, this@TaskFragment)
            taskRecyclerView.swapAdapter(taskAdapter, true)
            if (completeStatus == COMPLETED) {
                removeAllCompleted.visibility = View.VISIBLE
            }
        } else {
            removeAllCompleted.visibility = View.INVISIBLE
            emptyMessageTextView.visibility = View.VISIBLE
            when (completeStatus) {
                ACTIVE -> emptyMessageTextView.setText("No Active Tasks!")
                COMPLETED -> emptyMessageTextView.setText("No Completed Tasks Yet!")
            }
            taskRecyclerView.visibility = View.INVISIBLE
        }
    }

    override fun updateTaskCompleteStatus(taskId: Int, completeFlag: Boolean) {
        // Todo : Method for updating the complete status for the task
        toggleTodoMutationCloud(taskId, completeFlag)
    }

    fun addTodo(title: String) {
        // Todo : Add method to update todos
        addTodoMutationCloud(title)
    }

    private fun addTodoMutationCloud(title: String) {
        // Init Query
        val addTodoMutation = AddTodoMutation.builder().todo(title).isPublic(false).build()

        // Apollo runs query on background thread
        Network.apolloClient.mutate(addTodoMutation)?.enqueue(object : ApolloCall.Callback<AddTodoMutation.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo add", error.toString() )
            }

            override fun onResponse(@NotNull response: Response<AddTodoMutation.Data>) {
                Log.d("Todo add", response.data()?.toString() )
                val addedTodo = response.data()?.insert_todos()?.returning()?.get(0)
                val todo = GetMyTodosQuery.Todo(
                    addedTodo?.__typename()!!,
                    addedTodo.id(),
                    addedTodo.title(),
                    addedTodo.created_at(),
                    addedTodo.is_completed)
                Network.apolloClient
                    .apolloStore()
                    .write(GetMyTodosQuery(), GetMyTodosQuery.Data(mutableListOf(todo))).execute()
                getMyTodosQueryLocal()
            }
        })
    }

    private fun toggleTodoMutationCloud(todoId: Int, completeFlag: Boolean){
        // Init Query
        val toggleTodoMutation = ToggleTodoMutation.builder().id(todoId).isCompleted(completeFlag).build()
        // Apollo runs query on background thread
        val index = listItems.indexOfFirst { todo ->  todo.id() == todoId}
        val todos = listItems.toMutableList().get(index)


        val todo = GetMyTodosQuery.Todo(
            todos.__typename(),
            todos.id(),
            todos.title(),
            todos.created_at(),
            completeFlag)

        val updatedList = listItems.toMutableList()
        updatedList.set(index, todo)
        Network.apolloClient
            .apolloStore()
            .writeOptimisticUpdatesAndPublish(GetMyTodosQuery(), GetMyTodosQuery.Data(mutableListOf(todo)), UUID.randomUUID()).execute()
        getMyTodosQueryLocal()
        Network.apolloClient.mutate(toggleTodoMutation)?.enqueue(object : ApolloCall.Callback<ToggleTodoMutation.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo", error.toString() )
            }

            override fun onResponse(@NotNull response: Response<ToggleTodoMutation.Data>) {
                Network.apolloClient.apolloStore().write(toggleTodoMutation, response.data()!!)
                getMyTodosQueryLocal()
            }
        })
    }

    // Fetch Todos from local cache
    fun getMyTodosQueryLocal(){

        getMyTodosQuery = GetMyTodosQuery.builder().build()
        Network.apolloClient
            .query(getMyTodosQuery)
            .responseFetcher(ApolloResponseFetchers.CACHE_FIRST)
            .enqueue(object : ApolloCall.Callback<GetMyTodosQuery.Data>() {
                override fun onFailure(e: ApolloException) {
                    Log.d("Todo", e.toString())
                }

                override fun onResponse(response: Response<GetMyTodosQuery.Data>) {
                    response.data()?.todos()
                        ?.toMutableList()?.let {
                            listItems = it
                        }
                    activity?.runOnUiThread { updateTabs() }
                }
            })
    }

    override fun delete(taskId: Int) {
        removeTodoMutationCloud(taskId)
    }

    private fun removeTodoMutationCloud(todoId: Int){
        // Init Query
        val removeTodoMutation = RemoveTodoMutation.builder().id(todoId).build()

        // Apollo runs query on background thread
        Network.apolloClient.mutate(removeTodoMutation)?.enqueue(object : ApolloCall.Callback<RemoveTodoMutation.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo", error.toString() )
            }

            override fun onResponse(@NotNull response: Response<RemoveTodoMutation.Data>) {
                // get data from local cache and update the list
                val index = listItems.indexOfFirst { todo ->  todo.id() == todoId}
                val todos = (listItems.toMutableList()).removeAt(index)

                Network.apolloClient
                    .apolloStore()
                    .write(GetMyTodosQuery(), GetMyTodosQuery.Data(mutableListOf(todos))).execute()
                getMyTodosQueryLocal()
            }
        })
    }

    private fun removeAllCompleted() {
        // Todo : Method for clearing all completed task at once
        removeAllCompletedCloud()
    }

    private fun removeAllCompletedCloud(){
        // Init Query
        val clearCompletedMutation = ClearCompletedMutation.builder().build()

        // Apollo runs query on background thread
        Network.apolloClient.mutate(clearCompletedMutation)?.enqueue(object : ApolloCall.Callback<ClearCompletedMutation.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo", error.toString() )
            }

            override fun onResponse(@NotNull response: Response<ClearCompletedMutation.Data>) {
                // get data from local cache and update the list
                val todos = listItems?.filter { task -> task.is_completed }
                Network.apolloClient
                    .apolloStore()
                    .write(GetMyTodosQuery(), GetMyTodosQuery.Data(todos!!)).execute()
                getMyTodosQueryLocal()
            }
        })
    }

    companion object {
        const val ALL = "ALL"
        const val ACTIVE = "ACTIVE"
        const val COMPLETED = "COMPLETED"
        private var fragmentListener: FragmentListener? = null

        var listItems: MutableList<GetMyTodosQuery.Todo> = mutableListOf(
        )

        @JvmStatic
        fun newInstance(completeStatus: String): TaskFragment {
            return TaskFragment().apply {
                arguments = Bundle().apply {
                    putString(COMPLETE_STATUS, completeStatus)
                }
            }
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // check if parent Fragment implements listener
        if (parentFragment is FragmentListener) {
            fragmentListener = parentFragment as FragmentListener
        } else {
            throw RuntimeException("$parentFragment must implement FragmentListener")
        }

    }

    override fun onDetach() {
        super.onDetach()
        fragmentListener = null
    }

    // Queries & Mutations
    private fun getMyTodoQueryCloud() {
        // Init Query
        getMyTodosQuery = GetMyTodosQuery.builder().build()

        // Apollo runs query on background thread
        Network.apolloClient.query(getMyTodosQuery)?.enqueue(object : ApolloCall.Callback<GetMyTodosQuery.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo", error.toString() )
            }

            override fun onResponse(@NotNull response: Response<GetMyTodosQuery.Data>) {
                // Changing UI must be on UI thread
                Log.d("Todo", response.data().toString() )
                 response.data()?.todos()

                ?.toMutableList()?.let {
                         listItems = it
                    }

                activity?.runOnUiThread { updateTabs() }

            }
        })
    }

}