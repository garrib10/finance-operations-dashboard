package dev.portfolio.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "date_format", nullable = false, length = 16)
    private DateFormatPreference dateFormat = DateFormatPreference.MEDIUM;

    @Column(name = "transaction_page_size", nullable = false)
    private int transactionPageSize = 10;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    protected User() {
    }

    public User(
            String firstName,
            String lastName,
            String email,
            String passwordHash
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        String fullName = (firstName.trim() + " " + lastName.trim()).trim();
        int displayNameLength = Math.min(100, fullName.codePointCount(0, fullName.length()));
        this.displayName = fullName.isEmpty() ? "Account" : fullName.substring(
                0, fullName.offsetByCodePoints(0, displayNameLength)
        );
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DateFormatPreference getDateFormat() {
        return dateFormat;
    }

    public int getTransactionPageSize() {
        return transactionPageSize;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}