package com.example.boundarymeasurer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DistanceUnit { METERS, YARDS, FEET }

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BoundaryMeasurerApp(fusedLocationClient)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BoundaryMeasurerApp(fusedLocationClient: FusedLocationProviderClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val stadiumDao = db.stadiumDao()

    // State Management
    var currentUiLocation by remember { mutableStateOf<Location?>(null) }
    var pointA by remember { mutableStateOf<Location?>(null) }
    var selectedUnit by remember { mutableStateOf(DistanceUnit.METERS) }
    var stadiumNameInput by remember { mutableStateOf("") }
    val savedStadiums by stadiumDao.getAllStadiums().collectAsState(initial = emptyList())
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // Start live GPS tracking if permission is available
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { currentUiLocation = it }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            onDispose {}
        }
    }

    // Distance Calculation Logic
    val calculatedDistance = remember(currentUiLocation, pointA, selectedUnit) {
        if (pointA != null && currentUiLocation != null) {
            val meters = pointA!!.distanceTo(currentUiLocation!!)
            when (selectedUnit) {
                DistanceUnit.METERS -> "${meters.roundToInt()} m"
                DistanceUnit.YARDS -> "${(meters * 1.09361).roundToInt()} yd"
                DistanceUnit.FEET -> "${(meters * 3.28084).roundToInt()} ft"
            }
        } else {
            "---"
        }
    }

    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Cricket Boundary Measurer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        // Live Distance Display Box
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Distance from Center Pitch (A)", style = MaterialTheme.typography.bodyMedium)
                Text(calculatedDistance, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Unit Selector Tabs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DistanceUnit.values().forEach { unit ->
                ElevatedButton(
                    onClick = { selectedUnit = unit },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (selectedUnit == unit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(unit.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save Current Position Input
        OutlinedTextField(
            value = stadiumNameInput,
            onValueChange = { stadiumNameInput = it },
            label = { Text("Stadium Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                currentUiLocation?.let { loc ->
                    pointA = loc
                    if (stadiumNameInput.isNotBlank()) {
                        scope.launch {
                            stadiumDao.insertStadium(Stadium(name = stadiumNameInput, latitude = loc.latitude, longitude = loc.longitude))
                            stadiumNameInput = ""
                        }
                    }
                }
            },
            enabled = currentUiLocation != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Current Location as Center (A)")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Saved Grounds", style = MaterialTheme.typography.titleMedium)

        // List of saved stadiums
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(savedStadiums) { stadium ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stadium.name, style = MaterialTheme.typography.bodyLarge)
                            Text("Lat: ${stadium.latitude.toString().take(7)}, Lon: ${stadium.longitude.toString().take(7)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            Button(onClick = {
                                val loc = Location("SavedPoint").apply {
                                    latitude = stadium.latitude
                                    longitude = stadium.longitude
                                }
                                pointA = loc
                            }) {
                                Text("Load")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { scope.launch { stadiumDao.deleteStadium(stadium) } },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("X")
                            }
                        }
                    }
                }
            }
        }
    }
}
