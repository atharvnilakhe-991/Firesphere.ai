package ai.firesphere.notification;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.validation.InvalidRecipientException;
import ai.firesphere.notification.validation.RecipientValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RecipientValidatorTest {

    private RecipientValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RecipientValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"device-token-12345", "fcm_token_abcdef123456", "token:ios:abc-def-123"})
    @DisplayName("Valid push tokens pass validation")
    void testValidPushTokens(String token) {
        assertDoesNotThrow(() -> validator.validate(NotificationChannel.PUSH, token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "short", "token with spaces", "token\nnewline"})
    @DisplayName("Invalid push tokens fail validation")
    void testInvalidPushTokens(String token) {
        assertThrows(InvalidRecipientException.class, () -> validator.validate(NotificationChannel.PUSH, token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+14155552671", "919876543210", "+447911123456", "18005550199"})
    @DisplayName("Valid phone numbers pass SMS validation")
    void testValidSmsPhones(String phone) {
        assertDoesNotThrow(() -> validator.validate(NotificationChannel.SMS, phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "not-a-number", "+1-415-555-2671", "abcdefghij"})
    @DisplayName("Invalid phone numbers fail SMS validation")
    void testInvalidSmsPhones(String phone) {
        assertThrows(InvalidRecipientException.class, () -> validator.validate(NotificationChannel.SMS, phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {"officer@firesphere.ai", "chief.smith@dept.gov", "dispatch+alerts@fire.org"})
    @DisplayName("Valid emails pass validation")
    void testValidEmails(String email) {
        assertDoesNotThrow(() -> validator.validate(NotificationChannel.EMAIL, email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plainaddress", "@missingusername.com", "user@.com", "user@domain"})
    @DisplayName("Invalid emails fail validation")
    void testInvalidEmails(String email) {
        assertThrows(InvalidRecipientException.class, () -> validator.validate(NotificationChannel.EMAIL, email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+14155552671", "919876543210"})
    @DisplayName("Valid phone numbers pass WhatsApp validation")
    void testValidWhatsAppPhones(String phone) {
        assertDoesNotThrow(() -> validator.validate(NotificationChannel.WHATSAPP, phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "+123"})
    @DisplayName("Invalid phone numbers fail WhatsApp validation")
    void testInvalidWhatsAppPhones(String phone) {
        assertThrows(InvalidRecipientException.class, () -> validator.validate(NotificationChannel.WHATSAPP, phone));
    }
}
