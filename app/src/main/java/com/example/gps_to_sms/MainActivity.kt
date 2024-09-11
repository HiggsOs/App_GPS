package com.example.gps_to_sms
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper

import android.util.Log
import android.widget.TextView
import android.widget.Toast

import androidx.core.content.ContextCompat
import com.example.gps_to_sms.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CODIGO_PERMISO_SEGUNDO_PLANO = 100
    private var isPermisos = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermisos()




    }


    //Funcion Principal.
    private fun verificarPermisos() {
        val permisos = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,

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
            // ESta es el API para sacar la informacion del GPS

            fusedLocationClient.lastLocation.addOnSuccessListener {
                if (it != null) {
                    EnviarUbicacion(it)
                } else {
                    Toast.makeText(this, "No se puede obtener la ubicacion", Toast.LENGTH_SHORT).show()
                }
            }
            // Define la frecuencia a la cual se va a extraer la informacion y su precision

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                15000
            ).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build()
            //Cada que hay un cambio en la informacion del GPS este metodo lo añade a una lista.
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    //Extrae la ultima ubicacion y se la envia a la funcion EnviarUbicacion()
                    for (location in p0.locations) {
                        EnviarUbicacion(location)
                    }
                }
            }
            //Se escanea constantemente los datos de GPS
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
    companion object;

    @SuppressLint("SetTextI18n")
    private fun EnviarUbicacion(ubicacion: Location) {


        // Imprime los datos que se van recolectando en la UI

        binding.tvlat.text = "${ubicacion.latitude}"
        binding.tvlon.text = "${ubicacion.longitude}"
        binding.tvtime.text = "${ubicacion.time}"

        //Envia los datos al Log-cat para verificar que no alla errores.

        Log.d("GPS","LAT: ${ubicacion.latitude} - LONG: ${ubicacion.longitude} - Time: ${ubicacion.time} ")



        val message = " ${ubicacion.latitude},${ubicacion.longitude},${ubicacion.time}"

        //Enviar informacion a 2 servidores


        sendTcpData("hostgps.ddns.net",41000,message,binding.confirm1)

        sendTcpData("hostgps2.ddns.net",41000,message,binding.comfirm2)
        sendTcpData("hostgps3.ddns.net",41000,message,binding.comfirm2)
        sendTcpData("hostgps4.ddns.net",41000,message,binding.comfirm2)




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


    //Funciones para enviar Datos via TCP y via UDP

    private fun sendTcpData(ip: String, port: Int, message: String,statusText:TextView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Crear un socket TCP
                val socket = Socket(ip, port)
                val outputStream: OutputStream = socket.getOutputStream()

                // Enviar datos
                outputStream.write(message.toByteArray())
                outputStream.flush()

                // Cerrar el socket
                outputStream.close()
                socket.close()

                // Opcional: Imprimir en Log para confirmar envío
                Log.d("TCP", "Datos enviados correctamente: $message")
                runOnUiThread {
                    statusText.text = "Online"
                    statusText.setTextColor(Color.GREEN)
                }

            } catch (e: Exception) {
                // Manejar excepciones
                Log.e("TCP", "Error al enviar datos: ${e.message}")
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error al conetar con el sevidor TCP ${ip}", Toast.LENGTH_SHORT).show()
                    statusText.text = "Ofline"
                    statusText.setTextColor(Color.RED)
                }
                e.printStackTrace()


            }
        }
    }


    private fun sendUdpData(ip: String, port: Int , message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Convertir el mensaje a un array de bytes
                val messageBytes = message.toByteArray()

                // Crear el DatagramPacket para enviar los datos
                val packet = DatagramPacket(messageBytes, messageBytes.size, InetAddress.getByName(ip), port)

                // Crear un DatagramSocket
                val socket = DatagramSocket()

                // Enviar el paquete
                socket.send(packet)

                // Cerrar el socket
                socket.close()

                // Opcional: Imprimir en Log para confirmar envío
                Log.d("UDP", "Datos enviados correctamente: $message")


            } catch (e: Exception) {

                Log.e("UDP", "Error al enviar datos: ${e.message}")


                e.printStackTrace()
            }
        }
    }




}

