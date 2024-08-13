package com.example.gps_to_sms
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gps_to_sms.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CODIGO_PERMISO_SEGUNDO_PLANO = 100
    private var isPermisos = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    var phoneNumber:String="+573005680543"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermisos()

        binding.btnactualizar.setOnClickListener {

            phoneNumber=Actualizar_numero()
        }


    }


    //Funcion Principal.
    private fun verificarPermisos() {
        val permisos = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permisos.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val permisosArray = permisos.toTypedArray()
        if (tienePermisos(permisosArray)) {
            isPermisos = true
            onPermisosConcedidos()
        } else {
            solicitarPermisos(permisosArray)
        }
    }

    private fun tienePermisos(permisos: Array<String>): Boolean {
        return permisos.all {
            return ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun onPermisosConcedidos() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)





        try {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if (it != null) {
                    EnviarUbicacion(it)
                } else {
                    Toast.makeText(this, "No se puede obtener la ubicacion", Toast.LENGTH_SHORT).show()
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000
            ).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)

                    for (location in p0.locations) {
                        EnviarUbicacion(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {

        }
    }

    private fun solicitarPermisos(permisos: Array<String>) {
        requestPermissions(
            permisos,
            CODIGO_PERMISO_SEGUNDO_PLANO
        )
    }
    companion object {
        private const val REQUEST_CODE_SEND_SMS = 123
    }

    private fun EnviarUbicacion(ubicacion: Location) {


        // Imprime los datos que se van recolectando en la UI
        binding.tvnumero.text = "${phoneNumber}"
        binding.tvlat.text = "${ubicacion.latitude}"
        binding.tvlon.text = "${ubicacion.longitude}"
        binding.tvtime.text = "${ubicacion.time}"

        //Envia los datos al Log-cat para verificar que no alla errores.

        Log.d("GPS","LAT: ${ubicacion.latitude} - LONG: ${ubicacion.longitude} - Time: ${ubicacion.time} ")



        val message = "Latitude: ${ubicacion.latitude}, Longitude: ${ubicacion.longitude},Time stamp: ${ubicacion.time}"

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val smsManager = SmsManager.getDefault()

                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Toast.makeText(this, "SMS sent!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "SMS failed!", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQUEST_CODE_SEND_SMS
            )
        }
    }
    private fun Actualizar_numero(): String {
        val identificador = "+57"
        var numerofinal :String =""
        try {
            val numero = binding.inputphone.text.toString()
            if (numero.isNotEmpty() && numero.length == 10) {
                // Aquí puedes hacer lo que necesites con el número actualizado
                Toast.makeText(this, "Número actualizado: $numero", Toast.LENGTH_SHORT).show()
                numerofinal ="${identificador}${numero}"

            } else {
                Toast.makeText(this, "Numero no válido!", Toast.LENGTH_SHORT).show()
                numerofinal ="+573005680543"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo modificar el numero", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }



        return numerofinal
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == CODIGO_PERMISO_SEGUNDO_PLANO) {
            val todosPermisosConcedidos = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (grantResults.isNotEmpty() && todosPermisosConcedidos) {
                isPermisos = true
                onPermisosConcedidos()
            }
        }
    }
}

