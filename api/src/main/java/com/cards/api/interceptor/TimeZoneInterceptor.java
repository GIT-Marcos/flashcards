package com.cards.api.interceptor;

import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.event.UserTimeZoneUpdateEvent;
import com.cards.api.util.TimeZoneUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TimeZoneInterceptor implements HandlerInterceptor {

    private final ApplicationEventPublisher eventPublisher;

    public TimeZoneInterceptor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String zoneInfo = request.getHeader("Time-Zone");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (zoneInfo != null && auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            if (TimeZoneUtils.isValid(zoneInfo)) {
                if (auth.getPrincipal() instanceof SecurityUser securityUser
                        && zoneInfo.equals(securityUser.zoneInfo())) {
                    return true;
                }
                // NOTA: Este evento se publica fuera de cualquier transacción de base de datos
                // porque el interceptor se ejecuta en el ciclo de vida de una petición HTTP
                // (preHandle), que no tiene contexto transaccional. Es correcto usar @EventListener
                // (en lugar de @TransactionalEventListener) en el manejador, ya que no depende
                // de una transacción comprometida para dispararse. Si en el futuro el manejador
                // necesitara una transacción, debe añadirse @Transactional en la función del
                // listener o publicar el evento dentro de TransactionTemplate desde el origen.
                eventPublisher.publishEvent(new UserTimeZoneUpdateEvent(auth.getName(), zoneInfo));
            }
        }

        return true;
    }
}
