package com.example.gps_to_sms
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
import androidx.annotation.RequiresApi
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.UUID



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CODIGO_PERMISO_SEGUNDO_PLANO = 100
    private var isPermisos = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val OBD2_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private var rpmValue: Int? = 0 // Variable global para almacenar las RPM

    private var velocityValue: Int?=0

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermisos()




    }

    private fun checkBluetoothAdapter() {
        if (bluetoothAdapter == null) {
            binding.statusTextView.text = "Este dispositivo no soporta Bluetooth"
        } else {
            binding.statusTextView.text = "Bluetooth está disponible"
        }
    }

    //Funcion Principal.
    @RequiresApi(Build.VERSION_CODES.S)
    private fun verificarPermisos() {
        val permisos = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT

        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permisos.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val permisosArray = permisos.toTypedArray()
        if (tienePermisos(permisosArray)) {
            isPermisos = true
            onPermisosConcedidos()
            scanAndConnectToOBD2()
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
                5000
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

        sendRPMCommand()
        sendVelocityCommand()
        // Imprime los datos que se van recolectando en la UI

        binding.tvlat.text = "${ubicacion.latitude}"
        binding.tvlon.text = "${ubicacion.longitude}"
        binding.tvtime.text = "${ubicacion.time}"
        binding.textView5.text="${rpmValue}"
        binding.ValorVelocidad.text="${velocityValue}"
        //Envia los datos al Log-cat para verificar que no alla errores.

        Log.d("GPS","LAT: ${ubicacion.latitude} - LONG: ${ubicacion.longitude} - Time: ${ubicacion.time} - RPM: ${rpmValue} - - Velocity: ${velocityValue}")

        //HOLA

        val message = "${ubicacion.latitude}, ${ubicacion.longitude},${ubicacion.time},${rpmValue},${velocityValue},MXL306"

        //Enviar informacion a 2 servidores


        sendTcpData("hostgps.ddns.net",41000,message,binding.confirm1)

        sendTcpData("hostgps2.ddns.net",41000,message,binding.comfirm2)

        sendTcpData("hostgps3.ddns.net",41000,message,binding.confirm1)

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
    private fun scanAndConnectToOBD2() {
            binding.statusTextView.text = "Escaneando dispositivos..."
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
      
            pairedDevices?.forEach { device ->
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                if (device.name != null && device.name.contains("OBDII")) {
                    connectToDevice(device)
                    return
                }
            }

           Log.d("Emparajamiento","No se encontró OBD2 emparejado")
        }

    private fun connectToDevice(device: BluetoothDevice)
           {
                  try
                  {
                      if (ActivityCompat.checkSelfPermission
                              (
                              this
                              ,
                              Manifest.permission.BLUETOOTH_CONNECT

                          ) != PackageManager.PERMISSION_GRANTED

                      )
                      {
                          // TODO: Consider callin
                          //  g
                          //    ActivityCompat#requestPermission
                          //    s
                          // here to request the missing permissions, and then overridin
                          // g
                          //   public void onRequestPermissionsResult(int requestCode, String[] permissions
                          //   ,
                          //                                          int[] grantResults
                          //                                          )
                          // to handle the case where the user grants the permission. See the documentatio
                          // n
                          // for ActivityCompat#requestPermissions for more details
                          // .
                          return

                      }
                      bluetoothSocket = device.createRfcommSocketToServiceRecord(OBD2_UUID
                      )
                      if (ActivityCompat.checkSelfPermission
                              (
                              this
                              ,
                              Manifest.permission.BLUETOOTH_CONNECT

                          ) != PackageManager.PERMISSION_GRANTED

                      )
                      {
                          // TODO: Consider callin
                          //  g
                          //    ActivityCompat#requestPermission
                          //    s
                          // here to request the missing permissions, and then overridin
                          // g
                          //   public void onRequestPermissionsResult(int requestCode, String[] permissions
                          //   ,
                          //                                          int[] grantResults
                          //                                          )
                          // to handle the case where the user grants the permission. See the documentatio
                          // n
                          // for ActivityCompat#requestPermissions for more details
                          // .
                          return
                      }
                      bluetoothSocket?.connect(

                      )




                      // Iniciar el proceso de lectura de RP
                      // M
                      sendRPMCommand(

                      )
                      sendVelocityCommand()
                  } catch (e: IOException)
                  {
                      e.printStackTrace(

                      )
                      Log.e("Error de conexion","Error al conectar con OBD2")

                      try
                      {
                          bluetoothSocket?.close(

                          )
                      } catch (closeException: IOException)
                      {
                          closeException.printStackTrace(

                          )

                      }

                  }
   }

   private fun sendRPMCommand() {
           try {
               val outputStream = bluetoothSocket?.outputStream
               outputStream?.write("010C\r".toByteArray())
               outputStream?.flush()

               // Leer la respuesta después de enviar el comando
               readResponse()
           } catch (e: IOException) {
               e.printStackTrace()
               Log.e("Error sent rpm","Error al enviar comando de RPM")
           }
   }
   private fun readResponse() {
           try {
               val inputStream = bluetoothSocket?.inputStream
               val buffer = ByteArray(1024) // Buffer para la respuesta
               val bytes = inputStream?.read(buffer) ?: 0
               val response = String(buffer, 0, bytes).trim()

               // Mostrar la respuesta OBD2 en su formato original

               println("Respuesta completa del OBD2: $response")  // Añade esto para ver la respuesta en logcat

               // Calcular y mostrar las RPM
               rpmValue = calculateRPM(response)
               if (rpmValue != null) {
                   println("\nRPM: $rpmValue")
               } else {
                   println("\nNo se pudo calcular las RPM")
               }
           } catch (e: IOException) {
               e.printStackTrace()
               println( "Error al leer respuesta")
           }

   }
   private fun calculateRPM(response: String): Int? {
           return try {
               // Limpia la respuesta para eliminar espacios
               val cleanedResponse = response.replace(" ", "")

               // Busca "410C" en la respuesta limpia
               val hexIndex = cleanedResponse.indexOf("410C")
               if (hexIndex != -1 && hexIndex + 6 <= cleanedResponse.length) {
                   // Toma los 4 caracteres después de "410C" (dos bytes para las RPM)
                   val rpmHex = cleanedResponse.substring(hexIndex + 4, hexIndex + 8)

                   // Convierte a entero los datos de RPM
                   val rpmInt = rpmHex.toInt(16) // Convierte de hexadecimal a decimal
                   rpmInt / 4 // Divide por 4 para obtener el valor de RPM real
               } else {
                   null // Si no se encuentra "410C" o faltan datos, devuelve null
               }
           } catch (e: Exception) {
               null // Retorna null si hay un error en el parseo
           }
       }


    private fun sendVelocityCommand() {
        try {
            val outputStream = bluetoothSocket?.outputStream
            outputStream?.write("010D\r".toByteArray())
            outputStream?.flush()

            readVelocityResponse()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("Error sent velocity","Error al enviar comando de velocidad")
        }
    }

    private fun readVelocityResponse() {
        try {
            val inputStream = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            val bytes = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytes).trim()

            Log.e("Trama recibida de la solicitud de velocidad","Respuesta OBD2: $response")
            velocityValue = calculateVelocity(response)

            if (velocityValue != null) {
                Log.e("Valor de velocidad recibido","Velocidad: $velocityValue")
            } else {
                Log.e("Error al calcular velocidad","Error al calcular la velocidad")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("No se recibio la velocidad","Error al leer respuesta")
        }
    }

    private fun calculateVelocity(response: String): Int? {
        return try {
            val cleanedResponse = response.replace(" ", "")
            val hexIndex = cleanedResponse.indexOf("410D")
            if (hexIndex != -1 && hexIndex + 4 <= cleanedResponse.length) {
                val velocityHex = cleanedResponse.substring(hexIndex + 4, hexIndex + 6)
                velocityHex.toInt(16)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }




















































}
