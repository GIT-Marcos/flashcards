package com.cards.api.service;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.dto.SecurityUser;
import com.cards.api.dto.event.UserLoginEvent;
import com.cards.api.dto.event.UserTimeZoneUpdateEvent;
import com.cards.api.dto.request.ForgotPasswordRequest;
import com.cards.api.dto.request.LoginRequest;
import com.cards.api.dto.request.RegisterRequest;
import com.cards.api.dto.request.ResetPasswordRequest;
import com.cards.api.dto.response.AuthResponse;
import com.cards.api.dto.response.ForgotPasswordResponse;
import com.cards.api.dto.response.ResetPasswordResponse;
import com.cards.api.dto.response.SignupResponse;
import com.cards.api.entity.User;
import com.cards.api.exception.domain.*;
import com.cards.api.mapper.SecurityUserMapper;
import com.cards.api.repo.UserRepository;
import com.cards.api.service.notification.EmailService;
import com.cards.api.util.TimeZoneUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUserMapper securityUserMapper;
    private final EmailService emailService;
    private final ApplicationProperties properties;

    public AuthService(ApplicationEventPublisher eventPublisher, UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuthenticationManager authenticationManager, SecurityUserMapper securityUserMapper,
                       EmailService emailService, ApplicationProperties properties) {
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.securityUserMapper = securityUserMapper;
        this.emailService = emailService;
        this.properties = properties;
    }

    public SignupResponse signup(RegisterRequest request) {
        if (request.zoneInfo() == null || request.zoneInfo().isBlank()) {
            throw new InvalidTimeZoneException("Zone info must not be null or empty");
        }

        String zone = request.zoneInfo().strip();
        TimeZoneUtils.parseOrThrow(zone);

        if (userRepository.existsByUsernameIgnoreCase(request.username()))
            throw new DuplicatedUsernameException(request.username());

        if (userRepository.existsByEmailIgnoreCase(request.email().toLowerCase(Locale.ROOT)))
            throw new DuplicatedUserEmailException(request.email());

        String passwordHash = passwordEncoder.encode(request.password());
        String token = jwtService.generateEmailVerificationToken(
            request.username(), request.email(), passwordHash, zone);

        String verificationUrl = properties.getNotifications().getApiUrl() + "/auth/confirm?token=" + token;
        emailService.sendVerificationEmail(request.email(), request.username(), verificationUrl);

        return new SignupResponse(
            "Se ha enviado un email de verificación a " + request.email()
                + ". Revisa tu bandeja de entrada."
        );
    }

    @Transactional(readOnly = true)
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(request.email());
        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponse(
                "If an account with that email exists, a password reset link has been sent."
            );
        }

        User user = userOpt.get();
        String token = jwtService.generatePasswordResetToken(
            user.getId(), user.getEmail(), user.getPasswordHash()
        );

        String resetUrl = properties.getNotifications().getAppUrl() + "/auth/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetUrl);

        return new ForgotPasswordResponse(
            "If an account with that email exists, a password reset link has been sent."
        );
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        String rawToken = request.token().strip();

        if (!jwtService.isResetPasswordToken(rawToken)) {
            throw new InvalidResetPasswordTokenException(
                "The reset link is invalid or has expired. Please request a new one."
            );
        }

        JwtService.ResetPasswordData data;
        try {
            data = jwtService.extractResetPasswordData(rawToken);
        } catch (Exception ex) {
            throw new InvalidResetPasswordTokenException(
                "The reset link is invalid or has expired. Please request a new one."
            );
        }

        User user = userRepository.findById(data.userId())
            .orElseThrow(() -> new InvalidResetPasswordTokenException(
                "The reset link is invalid or has expired. Please request a new one."
            ));

        if (!user.getPasswordHash().equals(data.passwordHash())) {
            throw new InvalidResetPasswordTokenException(
                "This reset link has already been used. Please request a new one."
            );
        }

        String newHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newHash);
        userRepository.save(user);

        return new ResetPasswordResponse(
            "Your password has been successfully reset. You can now log in with your new password."
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String zoneInfo) {
        // 1. Autenticar usando el AuthenticationManager de Spring
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        // 2. Si llegamos aquí, las credenciales son correctas
        User user = userRepository.findByUsernameIgnoreCase(request.username())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 3. Generar el accessToken con el userId incluido
        SecurityUser securityUser = securityUserMapper.toSecurityUser(user);
        String jwt = jwtService.generateToken(securityUser);
        String refreshToken = jwtService.generateRefreshToken(securityUser);

        // 4. Publicar evento de login (síncrono, pero el listener es asíncrono)
        eventPublisher.publishEvent(new UserLoginEvent(user.getId()));

        // 5. Si el header Time-Zone viene y es distinto al actual, publicar evento de actualización
        if (zoneInfo != null && !zoneInfo.isBlank() && TimeZoneUtils.isValid(zoneInfo)
            && !zoneInfo.equals(user.getZoneInfo())) {
            eventPublisher.publishEvent(new UserTimeZoneUpdateEvent(user.getUsername(), zoneInfo));
        }

        return new AuthResponse(jwt, refreshToken, user.getUsername());
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new InvalidRefreshTokenException("Refresh token must not be null or empty");
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        try {
            final String username = jwtService.extractUsername(refreshToken);
            var user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

            SecurityUser securityUser = securityUserMapper.toSecurityUser(user);

            if (jwtService.isTokenValid(refreshToken, securityUser)) {
                String accessToken = jwtService.generateToken(securityUser);
                String newRefreshToken = jwtService.generateRefreshToken(securityUser);
                return new AuthResponse(accessToken, newRefreshToken, user.getUsername());
            }
        } catch (InvalidRefreshTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        throw new InvalidRefreshTokenException("Invalid refresh token");
    }
}
