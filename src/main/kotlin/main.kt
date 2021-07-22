import androidx.compose.desktop.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.matrixkt.models.events.MatrixEvent
import io.github.matrixkt.models.events.contents.room.*
import io.github.matrixkt.utils.MatrixJson
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Loader {
	fun loadEvents(): List<MatrixEvent> {
		val fileData = javaClass.getResource("room_events.json")!!.readText()
		val roomEvents = MatrixJson.decodeFromString<List<MatrixEvent>>(fileData)
		return roomEvents.apply {}
	}
}

fun main() {
	val roomEvents = Loader.loadEvents()

	Window {
		MaterialTheme {
			LazyColumn(reverseLayout = true) {
				itemsIndexed(roomEvents) { idx, event ->
					Column {
						Text("$idx", color = Color.Red)
						if (event.type == "m.room.message") {
							val sender = event.sender
							val prev = roomEvents.getOrNull(idx + 1)
							val next = roomEvents.getOrNull(idx - 1)
							val isNotFirst = prev != null && prev.sender == sender && prev.type == "m.room.message"
							val isNotLast = next != null && next.sender == sender && next.type == "m.room.message"
							MessageEvent(event, !isNotFirst, !isNotLast)
						} else {
							ChatItem(event)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ChatItem(event: MatrixEvent) {
	val text = when (event.type) {
		"m.room.member" -> {
			val content = MatrixJson.decodeFromJsonElement(MemberContent.serializer(), event.content)
			val prevContentJson = event.prevContent ?: event.unsigned?.get("prev_content") ?: JsonNull
			val prevContent = MatrixJson.decodeFromJsonElement(MemberContent.serializer().nullable, prevContentJson)

			buildAnnotatedString {
				append(event.stateKey ?: "Unknown user ")

				when (prevContent?.membership) {
					Membership.KNOCK -> TODO()
					Membership.BAN -> when (content.membership) {
						Membership.KNOCK -> TODO()
						Membership.INVITE, Membership.JOIN -> throw IllegalStateException("Must never happen")
						Membership.LEAVE -> append(" was unbanned")
						Membership.BAN -> append(" made no change")
					}
					Membership.LEAVE, null -> when (content.membership) {
						Membership.KNOCK -> TODO()
						Membership.INVITE -> append(" was invited")
						Membership.JOIN -> append(" joined")
						Membership.LEAVE -> append(" made no change")
						Membership.BAN -> append(" was banned")
					}
					Membership.JOIN -> when (content.membership) {
						Membership.KNOCK -> TODO()
						Membership.INVITE -> throw IllegalStateException("Must never happen")
						Membership.JOIN -> {
							val changedName = prevContent.displayName != content.displayName
							val changedAvatar = prevContent.avatarUrl != content.avatarUrl
							if (changedAvatar && changedName) {
								append(" changed their avatar and display name")
							} else if (changedAvatar) {
								append(" changed their avatar")
							} else if (changedName) {
								append(" changed display name")
							} else {
								append(" made no change")
							}
						}
						Membership.LEAVE -> append(if (event.stateKey == event.sender) " left" else " was kicked")
						Membership.BAN -> append(" was kicked and banned")
					}
					Membership.INVITE -> when (content.membership) {
						Membership.KNOCK -> TODO()
						Membership.INVITE -> append(" made no change")
						Membership.JOIN -> append(" joined")
						Membership.LEAVE -> append(if (event.stateKey == event.sender) " rejected invite" else "'s invitation was revoked")
						Membership.BAN -> append(" was banned")
					}
				}
			}
		}
		"m.room.name" -> {
			val content = MatrixJson.decodeFromJsonElement(NameContent.serializer(), event.content)
			AnnotatedString("${event.sender} updated the room name to '${content.name}'.")
		}
		"m.room.topic" -> {
			val content = MatrixJson.decodeFromJsonElement(TopicContent.serializer(), event.content)
			AnnotatedString("${event.sender} updated the topic to '${content.topic}'.")
		}
		"m.room.avatar" -> {
			AnnotatedString("${event.sender} updated the room avatar.")
		}
		"m.room.canonical_alias" -> {
			val content = MatrixJson.decodeFromJsonElement(CanonicalAliasContent.serializer(), event.content)
			AnnotatedString("${event.sender} set the room's canonical alias to '${content.alias}'.")
		}
		"m.room.guest_access" -> {
			val content = MatrixJson.decodeFromJsonElement(GuestAccessContent.serializer(), event.content)
			val action = when (content.guestAccess) {
				GuestAccess.CAN_JOIN -> "has allowed guests to join the room"
				GuestAccess.FORBIDDEN -> "disabled guest access"
			}
			AnnotatedString("${event.sender} ${action}.")
		}
		"m.room.create" -> {
			val content = MatrixJson.decodeFromJsonElement(CreateContent.serializer(), event.content)
			buildAnnotatedString {
				append(content.creator)
				append(" created this room")
				if (content.predecessor != null) {
					append(" to replace room '${content.predecessor?.roomId}'")
				}
			}
		}
		"m.room.join_rules" -> {
			val content = MatrixJson.decodeFromJsonElement(JoinRulesContent.serializer(), event.content)
			val action = when (content.joinRule) {
				JoinRule.PUBLIC -> "has allowed anyone to join the room."
				JoinRule.PRIVATE -> "has allowed anyone to join the room if they know the roomId."
				JoinRule.INVITE -> "made the room invite only."
				JoinRule.KNOCK -> "has set the join rule to 'KNOCK'."
			}
			AnnotatedString("${event.sender} ${action}.")
		}
		"m.room.history_visibility" -> {
			val content = MatrixJson.decodeFromJsonElement(HistoryVisibilityContent.serializer(), event.content)
			val action = when (content.historyVisibility) {
				HistoryVisibility.INVITED -> "has set history visibility to 'INVITED'."
				HistoryVisibility.JOINED -> "has set history visibility to 'JOINED'."
				HistoryVisibility.SHARED -> "made future room history visible to all room members."
				HistoryVisibility.WORLD_READABLE -> "has set history visibility to 'WORLD_READABLE'."
			}
			AnnotatedString("${event.sender} ${action}.")
		}
		"m.room.encryption" -> {
			AnnotatedString("${event.sender} has enabled End to End Encryption.")
		}
		else -> {
			AnnotatedString("Cannot render '${event.type}' yet" )
		}
	}
	@OptIn(ExperimentalMaterialApi::class)
	(ListItem {
		Text(text)
	})
}

@Composable
private fun MessageEvent(event: MatrixEvent, isFirstByAuthor: Boolean, isLastByAuthor: Boolean) {
	if (event.content.isEmpty()) {
		UserMessageDecoration(event, isFirstByAuthor, isLastByAuthor) {
			Text("**This message was redacted**")
		}
		return
	}

	val content = try {
		MatrixJson.decodeFromJsonElement(MessageContent.serializer(), event.content)
	} catch (e: Exception) {
		UserMessageDecoration(event, isFirstByAuthor, isLastByAuthor) {
			Text("**Failed to decode message**")
		}
		return
	}

	UserMessageDecoration(event, isFirstByAuthor, isLastByAuthor) {
		when (content) {
			is MessageContent.Text -> {
				Text(
					text = content.body,
					style = MaterialTheme.typography.body1
				)
			}
			is MessageContent.Notice -> {
				CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
					Text(
						text = content.body,
						style = MaterialTheme.typography.body1
					)
				}
			}
			is MessageContent.Emote -> {
				Text(
					text = content.body,
					style = MaterialTheme.typography.body1
				)
			}
			is MessageContent.Image -> {
				val width = content.info?.width
				val height = content.info?.height
				val specifiedSize = if (width != null && height != null) {
					Modifier
						.sizeIn(maxWidth = 720.dp, maxHeight = 720.dp)
						.aspectRatio(width.toFloat() / height.toFloat())
				} else {
					Modifier
				}
				Image(Icons.Outlined.BrokenImage, null, specifiedSize)
			}
			else -> {
				Text("This is a ${content::class.simpleName} message")
			}
		}
	}
}

@Composable
private fun UserMessageDecoration(
	event: MatrixEvent,
	isFirstByAuthor: Boolean,
	isLastByAuthor: Boolean,
	content: @Composable () -> Unit)
{
	Row(Modifier.padding(top = if(isFirstByAuthor) 8.dp else 0.dp)) {
		// Render author image on the left
		if (isFirstByAuthor) {
			val modifier = Modifier.padding(horizontal = 16.dp)
				.size(42.dp)
				.clip(CircleShape)
				.align(Alignment.Top)
			Image(Icons.Filled.Person, null, modifier, contentScale = ContentScale.Crop)
		} else {
			Spacer(Modifier.width(74.dp))
		}

		// Render message on the right
		Column(Modifier.weight(1f)) {
			if (isFirstByAuthor) {
				AuthorAndTimeStamp(event.sender, event.originServerTimestamp)
			}

			content()

			Spacer(Modifier.height(if (isLastByAuthor) 8.dp else 4.dp))
		}
	}
}

@Composable
private fun AuthorAndTimeStamp(senderUserId: String, originServerTimestamp: Long) {
	Row {
		Text(
			text = senderUserId.take(10),
			style = MaterialTheme.typography.subtitle1,
			fontWeight = FontWeight.Bold,
			modifier = Modifier.alignBy(LastBaseline)
				.paddingFrom(LastBaseline, after = 8.dp) // Space to 1st bubble
		)
		Spacer(Modifier.width(8.dp))
		CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
			// TODO: Get ZoneId from compose and watch for system changes
			Text(
				text = Instant.ofEpochMilli(originServerTimestamp).atZone(ZoneId.systemDefault())
					.format(DateTimeFormatter.ofPattern("HH:mm")),
				style = MaterialTheme.typography.caption,
				modifier = Modifier.alignBy(LastBaseline)
			)
		}
	}
}
