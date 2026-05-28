package com.opencontinuity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

data class StrokeData(
    val points: List<Offset>,
    val color: Color,
    val tool: NoteTool,
    val thickness: Float = 5f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTakerScreen(navController: NavController) {
    val connectionManager = OpenContinuityApp.instance.connectionManager
    val noteSyncManager = OpenContinuityApp.instance.noteSyncManager
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var currentTool by remember { mutableStateOf(NoteTool.PEN) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val currentThickness by remember { mutableStateOf(5f) }

    val strokes = remember { mutableStateListOf<StrokeData>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val colors = listOf(
        Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color(0xFFFF9800) // Orange
    )

    fun sendSyncPayload(payload: NoteSyncPayload) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            connectionManager.broadcast(
                ProtocolMessage(
                    type = MessageType.NOTE_SYNC,
                    payload = protocolJson.encodeToJsonElement(payload)
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        noteSyncManager.incoming.collectLatest { payload ->
            when (payload.action) {
                NoteSyncAction.CLEAR -> strokes.clear()
                NoteSyncAction.STROKE -> {
                    if (payload.points.size > 1) {
                        val strokeColor = try {
                            Color(android.graphics.Color.parseColor(payload.color))
                        } catch (_: Exception) {
                            Color.Black
                        }
                        strokes.add(
                            StrokeData(
                                payload.points.map { Offset(it.x, it.y) },
                                strokeColor,
                                payload.tool,
                                payload.thickness
                            )
                        )
                    }
                }
                NoteSyncAction.PAN -> {
                    val px = payload.panX ?: 0f
                    val py = payload.panY ?: 0f
                    offset = Offset(offset.x + px, offset.y + py)
                }
                NoteSyncAction.ZOOM -> {
                    payload.zoom?.let { z -> scale = maxOf(0.1f, scale * z) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note Taker") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        strokes.clear()
                        sendSyncPayload(NoteSyncPayload(
                            action = NoteSyncAction.CLEAR,
                            tool = currentTool,
                            color = "#000000",
                            thickness = currentThickness
                        ))
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Canvas")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolButton(
                    icon = Icons.Default.Create,
                    text = "Pen",
                    selected = currentTool == NoteTool.PEN,
                    onClick = { currentTool = NoteTool.PEN }
                )
                ToolButton(
                    icon = Icons.Default.OpenWith,
                    text = "Pan/Zoom",
                    selected = currentTool == NoteTool.CURSOR,
                    onClick = { currentTool = NoteTool.CURSOR }
                )
                // Using a simple text for eraser as it has no default icon in basic material-icons setup easily accessible sometimes
                Button(
                    onClick = { currentTool = NoteTool.ERASER },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentTool == NoteTool.ERASER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (currentTool == NoteTool.ERASER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Eraser")
                }
            }

            // Colors (only visible if Pen is selected)
            if (currentTool == NoteTool.PEN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(32.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (currentColor == color) 3.dp else 1.dp,
                                    color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { currentColor = color }
                        )
                    }
                }
            }

            // Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .pointerInput(currentTool) {
                        if (currentTool == NoteTool.CURSOR) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = maxOf(0.1f, scale * zoom)
                                offset += pan / scale
                                sendSyncPayload(NoteSyncPayload(
                                    action = NoteSyncAction.ZOOM, // Overloaded for both pan and zoom based on payload
                                    tool = currentTool,
                                    color = "#000000",
                                    thickness = currentThickness,
                                    panX = pan.x,
                                    panY = pan.y,
                                    zoom = zoom
                                ))
                                sendSyncPayload(NoteSyncPayload(
                                    action = NoteSyncAction.PAN, // Overloaded for both pan and zoom based on payload
                                    tool = currentTool,
                                    color = "#000000",
                                    thickness = currentThickness,
                                    panX = pan.x,
                                    panY = pan.y,
                                    zoom = zoom
                                ))
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                    
                                    // Live sync snippet
                                    val hexColor = String.format("#%06X", 0xFFFFFF and currentColor.toArgb())
                                    sendSyncPayload(NoteSyncPayload(
                                        action = NoteSyncAction.STROKE,
                                        tool = currentTool,
                                        color = hexColor,
                                        thickness = currentThickness,
                                        points = currentStroke.map { NotePoint(it.x, it.y) }
                                    ))
                                },
                                onDragEnd = {
                                    strokes.add(StrokeData(currentStroke, currentColor, currentTool, currentThickness))
                                    currentStroke = emptyList()
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x * scale,
                    translationY = offset.y * scale
                )) {
                    strokes.forEach { stroke ->
                        if (stroke.points.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke.points.first().x, stroke.points.first().y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = if (stroke.tool == NoteTool.ERASER) Color.White else stroke.color,
                                style = Stroke(
                                    width = if (stroke.tool == NoteTool.ERASER) stroke.thickness * 4 else stroke.thickness,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (currentStroke.size > 1) {
                        val path = Path().apply {
                            moveTo(currentStroke.first().x, currentStroke.first().y)
                            for (i in 1 until currentStroke.size) {
                                lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = if (currentTool == NoteTool.ERASER) Color.White else currentColor,
                            style = Stroke(
                                width = if (currentTool == NoteTool.ERASER) currentThickness * 4 else currentThickness,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text)
    }
}
