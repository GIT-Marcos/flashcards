package com.cards.api.service.ai;

import com.cards.api.exception.domain.UnsupportedAiProviderException;
import com.cards.api.util.AiProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fábrica que selecciona la implementación de {@link AiClient} adecuada según el
 * {@link AiProvider} solicitado.
 * <p>
 * Implementa el patrón Factory + Registry: Spring inyecta automáticamente todos los
 * beans que implementan {@link AiClient} y los registra en un {@code EnumMap} usando
 * {@link AiClient#provider()} como clave. Esto permite añadir nuevos proveedores de IA
 * sin modificar código existente (Open/Closed Principle).
 * </p>
 *
 * <p><b>Uso:</b></p>
 * <pre>{@code
 * var client = factory.getClient(user.getAiProvider());
 * var cards = client.generateCards(...);
 * }</pre>
 *
 * @see AiClient
 * @see AiProvider
 * @see UnsupportedAiProviderException
 */
@Component
public class AiClientFactory {

    private final Map<AiProvider, AiClient> clients;

    public AiClientFactory(List<AiClient> clientList) {
        this.clients = new EnumMap<>(AiProvider.class);
        for (var client : clientList) {
            this.clients.put(client.provider(), client);
        }
    }

    /**
     * Devuelve el cliente de IA para el proveedor indicado.
     *
     * @param provider proveedor de IA solicitado
     * @return implementación concreta de {@link AiClient}
     * @throws UnsupportedAiProviderException si no hay cliente registrado para ese proveedor
     */
    public AiClient getClient(AiProvider provider) {
        var client = clients.get(provider);
        if (client == null) {
            throw new UnsupportedAiProviderException(provider);
        }
        return client;
    }
}
