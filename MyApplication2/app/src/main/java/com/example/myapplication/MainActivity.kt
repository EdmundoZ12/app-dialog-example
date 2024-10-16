package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.view.LayoutInflater
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import com.google.common.collect.Lists
import java.util.*

const val USUARIO = 0
const val BOT = 1
const val ENTRADA_DE_VOZ = 2

class MainActivity : AppCompatActivity() {

    // Variables
    private var cliente: SessionsClient? = null
    private var sesion: SessionName? = null
    private val uuid: String = UUID.randomUUID().toString()
    private var asistente_voz: TextToSpeech? = null

    // Views
    private lateinit var cajadetexto: EditText
    private lateinit var enviar: ImageView
    private lateinit var microfono: ImageView
    private lateinit var linear_chat: LinearLayout
    private lateinit var scroll_chat: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referenciamos los elementos del layout
        cajadetexto = findViewById(R.id.cajadetexto)
        enviar = findViewById(R.id.enviar)
        microfono = findViewById(R.id.microfono)
        linear_chat = findViewById(R.id.linear_chat)
        scroll_chat = findViewById(R.id.scroll_chat)

        // Desplazamos el ScrollView hacia abajo para mostrar siempre el último mensaje
        scroll_chat.post {
            scroll_chat.fullScroll(ScrollView.FOCUS_DOWN)
        }

        // Evento para enviar mensaje con Enter
        cajadetexto.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                enviarMensaje(enviar)
                true
            } else {
                false
            }
        }

        // Evento para el botón enviar
        enviar.setOnClickListener(this::enviarMensaje)

        // Evento para el botón del micrófono
        microfono.setOnClickListener(this::enviarMensajeMicrofono)

        // Iniciamos el asistente de Dialogflow
        iniciarAsistente()

        // Iniciamos el asistente de voz
        iniciarAsistenteVoz()
    }

    // Función iniciarAsistente
    private fun iniciarAsistente() {
        try {
            // Archivo JSON de configuración de la cuenta de Dialogflow (Google Cloud Platform)
            val config = resources.openRawResource(R.raw.credenciales)

            // Leemos las credenciales de la cuenta de Dialogflow (Google Cloud Platform)
            val credenciales = GoogleCredentials.fromStream(config)
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"))

            // Leemos el 'projectId' el cual se encuentra en el archivo 'credenciales.json'
            val projectId = (credenciales as ServiceAccountCredentials).projectId

            // Construimos una configuración para acceder al servicio de Dialogflow (Google Cloud Platform)
            val generarConfiguracion: SessionsSettings.Builder = SessionsSettings.newBuilder()

            // Configuramos las sesiones que usaremos en la aplicación
            val configurarSesiones: SessionsSettings =
                generarConfiguracion.setCredentialsProvider(
                    FixedCredentialsProvider.create(
                        credenciales
                    )
                ).build()
            cliente = SessionsClient.create(configurarSesiones)
            sesion = SessionName.of(projectId, uuid)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Función iniciarAsistenteVoz
    private fun iniciarAsistenteVoz() {
        asistente_voz = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.ERROR) {
                asistente_voz?.language = Locale("es")
            }
        }
    }

    private fun enviarMensaje(view: View) {
        val mensaje = cajadetexto.text.toString()

        if (mensaje.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.placeholder), Toast.LENGTH_LONG).show()
        } else {
            agregarTexto(mensaje, USUARIO)
            cajadetexto.setText("")

            // Enviamos la consulta del usuario al Bot
            val ingresarConsulta = QueryInput.newBuilder()
                .setText(TextInput.newBuilder().setText(mensaje).setLanguageCode("es")).build()
            solicitarTarea(this, sesion!!, cliente!!, ingresarConsulta).execute()
        }
    }

    private fun enviarMensajeMicrofono(view: View) {
        val intento = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intento.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intento.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intento.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.mensajedevoz))

        try {
            startActivityForResult(intento, ENTRADA_DE_VOZ)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(
                applicationContext,
                getString(R.string.mensajedevoznoadmitido),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun agregarTexto(mensaje: String, type: Int) {
        val layoutFrm: FrameLayout = when (type) {
            USUARIO -> agregarTextoUsuario()
            BOT -> agregarTextoBot()
            else -> agregarTextoBot()
        }

        layoutFrm.isFocusableInTouchMode = true
        linear_chat.addView(layoutFrm)

        val textview = layoutFrm.findViewById<TextView>(R.id.msg_chat)
        textview.text = mensaje

        scroll_chat.post {
            scroll_chat.fullScroll(ScrollView.FOCUS_DOWN)
        }

        if (type != USUARIO) {
            asistente_voz?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun agregarTextoUsuario(): FrameLayout {
        val inflater = LayoutInflater.from(this)
        return inflater.inflate(R.layout.mensaje_usuario, null) as FrameLayout
    }

    fun agregarTextoBot(): FrameLayout {
        val inflater = LayoutInflater.from(this)
        return inflater.inflate(R.layout.mensaje_bot, null) as FrameLayout
    }

    fun validar(response: DetectIntentResponse?) {
        try {
            if (response != null) {
                val respuestaBot = response.queryResult.fulfillmentText.ifEmpty {
                    response.queryResult.fulfillmentMessagesList[0].text.textList[0]
                }

                agregarTexto(respuestaBot, BOT)
            } else {
                agregarTexto(getString(R.string.audio_no_se_entiende), BOT)
            }
        } catch (e: Exception) {
            agregarTexto(getString(R.string.ingresa_mensaje), BOT)
        }
    }

    override fun onActivityResult(codigoSolicitud: Int, codigoResultado: Int, datos: Intent?) {
        super.onActivityResult(codigoSolicitud, codigoResultado, datos)

        if (codigoSolicitud == ENTRADA_DE_VOZ && codigoResultado == RESULT_OK && datos != null) {
            val resultado = datos.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            cajadetexto.text = Editable.Factory.getInstance().newEditable(resultado?.get(0))
            enviarMensaje(microfono)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        asistente_voz?.stop()
        asistente_voz?.shutdown()
    }
}
