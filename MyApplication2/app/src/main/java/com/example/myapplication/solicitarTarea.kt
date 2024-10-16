package com.example.myapplication

import android.app.Activity
import android.os.AsyncTask // Importa AsyncTask del paquete android.os
import com.google.cloud.dialogflow.v2.* // Importación de Dialogflow clases

class solicitarTarea(
    private val actividad: Activity,
    private val sesion: SessionName,
    private val sesionesCliente: SessionsClient,
    private val entradaConsulta: QueryInput
) : AsyncTask<Void, Void, DetectIntentResponse>() { // Asegúrate de que AsyncTask sea el correcto

    // Ejecutamos las tareas en segundo plano
    override fun doInBackground(vararg params: Void?): DetectIntentResponse? {
        return try {
            val detectarIntentoSolicitarTarea =
                DetectIntentRequest.newBuilder().setSession(sesion.toString())
                    .setQueryInput(entradaConsulta).build()
            sesionesCliente.detectIntent(detectarIntentoSolicitarTarea)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Se ejecuta en la interfaz de usuario después de completar la tarea
    override fun onPostExecute(result: DetectIntentResponse?) {
        (actividad as MainActivity).validar(result)
    }
}
