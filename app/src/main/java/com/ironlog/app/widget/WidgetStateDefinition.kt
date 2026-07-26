package com.ironlog.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Glance state definition that persists [WidgetState] as JSON in a per-widget DataStore file.
 */
object WidgetStateDefinition : GlanceStateDefinition<WidgetState> {

    private const val DATA_STORE_FILENAME = "ironlog_widget_state"

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> =
        DataStoreFactory.create(
            serializer = WidgetStateSerializer,
            produceFile = { File(context.dataDir, "datastore/$DATA_STORE_FILENAME-$fileKey.json") },
        )

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.dataDir, "datastore/$DATA_STORE_FILENAME-$fileKey.json")
}

private object WidgetStateSerializer : Serializer<WidgetState> {
    override val defaultValue: WidgetState = WidgetState()

    override suspend fun readFrom(input: InputStream): WidgetState =
        runCatching {
            Json.decodeFromString<WidgetState>(input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: WidgetState, output: OutputStream) {
        output.write(Json.encodeToString(WidgetState.serializer(), t).toByteArray())
    }
}
