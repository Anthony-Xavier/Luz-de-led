package com.example.ledjava;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.content.ContextCompat;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;

public class MainActivity extends AppCompatActivity {

    private MqttAsyncClient mqttClient;
    private final String MQTT_BROKER = "tcp://192.168.200.125:1883";
    private final String TOPIC = "Metal1";

    private Button bt_on_off;
    private SeekBar seekBar; // Variável para a SeekBar
    private boolean isOn = false; // Controle de estado
    private int intensity = 50; // Valor inicial de intensidade

    private String clientId = "Anthony";

    private void animateButton(Button button, int startColor, int endColor) {
        ObjectAnimator animator = ObjectAnimator.ofArgb(
                button, "backgroundColor", startColor, endColor);
        animator.setDuration(300); // Duração da animação em milissegundos
        animator.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_on_off = findViewById(R.id.bt_on_off);
        seekBar = findViewById(R.id.seekBar); // Inicializando a SeekBar

        // Define a cor inicial do botão para OFF
        ViewCompat.setBackgroundTintList(bt_on_off,
                ContextCompat.getColorStateList(this, R.color.RED));

        connectToMqttBroker();

        // Configura o botão de ON/OFF para alternar entre ON e OFF
        bt_on_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isOn) {
                    bt_on_off.setText("OFF");
                    animateButton(bt_on_off, Color.GREEN, Color.RED); // Animação de verde para vermelho
                    ViewCompat.setBackgroundTintList(bt_on_off,
                            ContextCompat.getColorStateList(MainActivity.this, R.color.RED));
                    publishMessage("OFF"); // Envia comando para desligar o LED
                    seekBar.setProgress(0); // Reseta a SeekBar para 0
                } else {
                    bt_on_off.setText("ON");
                    animateButton(bt_on_off, Color.RED, Color.GREEN); // Animação de vermelho para verde
                    ViewCompat.setBackgroundTintList(bt_on_off,
                            ContextCompat.getColorStateList(MainActivity.this, R.color.GREEN));
                    publishMessage("ON"); // Envia comando para ligar o LED
                    seekBar.setProgress(intensity); // Ajusta a SeekBar para o valor atual de intensidade
                }
                isOn = !isOn; // Alterna o estado
            }
        });

        // Configura a SeekBar para ajustar a intensidade
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intensity = progress; // Atualiza a intensidade com o valor da SeekBar
                // Não envia a mensagem para o servidor aqui, só atualiza a intensidade
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Não é necessário implementar, mas aqui poderia haver alguma lógica
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Envia a intensidade para o servidor quando o usuário parar de mexer
                publishMessage(String.valueOf(intensity)); // Envia a intensidade para o MQTT

                // Se a intensidade for 0, desliga o LED
                if (intensity == 0 && isOn) {
                    isOn = false;
                    bt_on_off.setText("OFF");
                    animateButton(bt_on_off, Color.GREEN, Color.RED); // Animação de OFF
                    ViewCompat.setBackgroundTintList(bt_on_off,
                            ContextCompat.getColorStateList(MainActivity.this, R.color.RED));
                    publishMessage("OFF"); // Desliga o LED
                } else if (intensity > 0 && !isOn) {
                    // Se a intensidade for maior que 0 e o LED estiver desligado, liga o LED
                    isOn = true;
                    bt_on_off.setText("ON");
                    animateButton(bt_on_off, Color.RED, Color.GREEN);
                    ViewCompat.setBackgroundTintList(bt_on_off,
                            ContextCompat.getColorStateList(MainActivity.this, R.color.GREEN));
                    publishMessage("ON"); // Liga o LED
                }
            }
        });

        // Adaptando o botão ao estado inicial do LED
        if (intensity > 0) {
            isOn = true;
            bt_on_off.setText("ON");
            animateButton(bt_on_off, Color.RED, Color.GREEN);
            ViewCompat.setBackgroundTintList(bt_on_off,
                    ContextCompat.getColorStateList(MainActivity.this, R.color.GREEN));
            seekBar.setProgress(intensity); // Ajusta a SeekBar para a intensidade atual
        } else {
            bt_on_off.setText("OFF");
            animateButton(bt_on_off, Color.GREEN, Color.RED);
            ViewCompat.setBackgroundTintList(bt_on_off,
                    ContextCompat.getColorStateList(MainActivity.this, R.color.RED));
            seekBar.setProgress(0); // Ajusta a SeekBar para 0 quando o LED estiver desligado
        }
    }

    private void connectToMqttBroker() {
        try {
            // Usando o clientId com o seu nome
            mqttClient = new MqttAsyncClient(MQTT_BROKER, clientId, null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    cause.printStackTrace(); // Lógica quando a conexão for perdida
                    reconnect(); // Chama a função de reconexão
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String receivedMessage = new String(message.getPayload());
                    System.out.println("Mensagem recebida: " + receivedMessage);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("Mensagem entregue com sucesso!");
                }
            });

            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("Conectado ao broker MQTT");
                    subscribeToTopic(); // Inscreve-se no tópico após conexão
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    exception.printStackTrace(); // Falha ao conectar
                    reconnect(); // Chama a função de reconexão
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
            reconnect(); // Chama a função de reconexão em caso de erro
        }
    }

    private void reconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!mqttClient.isConnected()) {
                        System.out.println("Tentando reconectar ao broker...");
                        mqttClient.connect();
                        Thread.sleep(5000);
                    }
                    System.out.println("Reconectado com sucesso!");
                } catch (MqttException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe(TOPIC, 1);
            System.out.println("Inscrito no tópico: " + TOPIC);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishMessage(String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            mqttClient.publish(TOPIC, mqttMessage);
            System.out.println("Mensagem publicada: " + message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Verifica se o cliente MQTT está conectado e desconecta para liberar recursos
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect(); // Desconecta do servidor MQTT
                System.out.println("Desconectado do servidor MQTT");
            } catch (MqttException e) {
                e.printStackTrace(); // Em caso de erro na desconexão
            }
        }
    }
}

