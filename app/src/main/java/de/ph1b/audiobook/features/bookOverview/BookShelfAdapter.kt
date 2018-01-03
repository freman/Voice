package de.ph1b.audiobook.features.bookOverview

import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.ph1b.audiobook.R
import de.ph1b.audiobook.data.Book
import de.ph1b.audiobook.injection.App
import de.ph1b.audiobook.injection.PrefKeys
import de.ph1b.audiobook.misc.coverFile
import de.ph1b.audiobook.misc.layoutInflater
import de.ph1b.audiobook.misc.onFirstPreDraw
import de.ph1b.audiobook.misc.supportTransitionName
import de.ph1b.audiobook.persistence.pref.Pref
import de.ph1b.audiobook.uitools.CoverReplacement
import de.ph1b.audiobook.uitools.maxImageSize
import de.ph1b.audiobook.uitools.visible
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.book_shelf_row.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

class BookShelfAdapter(
    private val context: Context,
    private val bookClicked: (Book, ClickType) -> Unit
) : RecyclerView.Adapter<BookShelfAdapter.ViewHolder>() {

  private val books = ArrayList<Book>()

  @field:[Inject Named(PrefKeys.CURRENT_BOOK)]
  lateinit var currentBookIdPref: Pref<Long>

  init {
    App.component.inject(this)
    setHasStableIds(true)
  }

  private fun formatTime(ms: Int): String {
    val h = "%02d".format((TimeUnit.MILLISECONDS.toHours(ms.toLong())))
    val m = "%02d".format((TimeUnit.MILLISECONDS.toMinutes(ms.toLong()) % 60))
    return h + ":" + m
  }

  /** Adds a new set of books and removes the ones that do not exist any longer **/
  fun newDataSet(newBooks: List<Book>) {
    val diffResult = DiffUtil.calculateDiff(
        object : DiffUtil.Callback() {

          override fun getOldListSize(): Int = books.size

          override fun getNewListSize(): Int = newBooks.size

          override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = books[oldItemPosition]
            val newItem = newBooks[newItemPosition]
            return oldItem.id == newItem.id && oldItem.position == newItem.position && oldItem.name == newItem.name
          }

          override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = books[oldItemPosition]
            val newItem = newBooks[newItemPosition]
            return oldItem.id == newItem.id
          }
        }, false
    ) // no need to detect moves as the list is sorted

    books.clear()
    books.addAll(newBooks)

    diffResult.dispatchUpdatesTo(this)
  }

  fun reloadBookCover(bookId: Long) {
    val index = books.indexOfFirst { it.id == bookId }
    if (index >= 0) {
      notifyItemChanged(index)
    }
  }

  override fun getItemId(position: Int) = books[position].id

  fun getItem(position: Int): Book = books[position]

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

  override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(books[position])

  override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) = when {
    payloads.isEmpty() -> onBindViewHolder(holder, position)
    else -> holder.bind(books[position])
  }

  override fun getItemCount(): Int = books.size

  inner class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
      parent.layoutInflater().inflate(
          R.layout.book_shelf_row,
          parent,
          false
      )
  ), LayoutContainer {

    override val containerView: View? get() = itemView

    private val progressBar = progress
    // private val leftTime: TextView = itemView.findViewById(R.id.leftTime)
    // private val rightTime: TextView = itemView.findViewById(R.id.rightTime)
    val coverView: ImageView = cover
    private val currentPlayingIndicator: ImageView = playingIndicator
    private val titleView: TextView = title
    private val editBook: View = edit
    var indicatorVisible: Boolean = false
      private set

    fun bind(book: Book) {
      //setting text
      val name = book.name
      titleView.text = name
      author.text = book.author
      author.visible = book.author != null
      titleView.maxLines = if (book.author == null) 2 else 1
      bindCover(book)

      indicatorVisible = book.id == currentBookIdPref.value
      currentPlayingIndicator.visible = false

      itemView.setOnClickListener { bookClicked(getItem(adapterPosition), ClickType.REGULAR) }
      editBook.setOnClickListener { bookClicked(getItem(adapterPosition), ClickType.MENU) }

      coverView.supportTransitionName = book.coverTransitionName

      val globalPosition = book.position
      val totalDuration = book.duration
      val progress = globalPosition.toFloat() / totalDuration.toFloat()

      //    leftTime.text = formatTime(globalPosition)
      progressBar.progress = progress
      //  rightTime.text = formatTime(totalDuration)
    }

    private fun bindCover(book: Book) {
      // (Cover)
      val coverFile = book.coverFile()
      val coverReplacement = CoverReplacement(book.name, context)

      if (coverFile.canRead() && coverFile.length() < maxImageSize) {
        Picasso.with(context)
            .load(coverFile)
            .placeholder(coverReplacement)
            .into(coverView)
      } else {
        Picasso.with(context)
            .cancelRequest(coverView)
        // we have to set the replacement in onPreDraw, else the transition will fail.
        coverView.onFirstPreDraw { coverView.setImageDrawable(coverReplacement) }
      }
    }
  }

  enum class ClickType {
    REGULAR,
    MENU
  }
}
