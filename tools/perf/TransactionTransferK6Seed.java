import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TransactionTransferK6Seed {

    private static final String PG_URL = env("PG_URL", "jdbc:postgresql://localhost:5432/xcp");
    private static final String PG_USER = env("PG_USER", "iyongjun");
    private static final String PG_PASSWORD = env("PG_PASSWORD", "");
    private static final String JWT_SECRET = requiredEnv("JWT_SECRET");
    private static final int PAIR_COUNT = intEnv("TRANSACTION_TRANSFER_K6_PAIRS", 20);
    private static final String CURRENCY = env("TRANSACTION_TRANSFER_K6_CURRENCY", "KRW");
    private static final BigDecimal TRANSFER_AMOUNT = decimalEnv("TRANSACTION_TRANSFER_K6_AMOUNT", "1.00");
    private static final BigDecimal SENDER_BALANCE = decimalEnv("TRANSACTION_TRANSFER_K6_SENDER_BALANCE", "1000000000.00");
    private static final BigDecimal RECEIVER_BALANCE = decimalEnv("TRANSACTION_TRANSFER_K6_RECEIVER_BALANCE", "0.00");
    private static final String PASSWORD = env("TRANSACTION_TRANSFER_K6_PASSWORD", "Benchmark123!");
    private static final Path OUTPUT_DIR = Path.of("build", "perf", "k6");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        try (Connection connection = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD)) {
            connection.setAutoCommit(false);
            List<TransferPair> pairs = createPairs(connection);
            connection.commit();

            writeEnv(pairs);
            writeReadme(pairs);

            System.out.println("Transaction transfer k6 seed completed.");
            System.out.println("- pairs: " + pairs.size());
            System.out.println("- currency: " + CURRENCY);
            System.out.println("- transferAmount: " + TRANSFER_AMOUNT);
            System.out.println("- senderUserIds: " + join(pairs.stream().map(TransferPair::senderUserId).toList()));
            System.out.println("- receiverUserIds: " + join(pairs.stream().map(TransferPair::receiverUserId).toList()));
            System.out.println("- auth env: " + OUTPUT_DIR.resolve("transaction-transfer-auth.env"));
        }
    }

    private static List<TransferPair> createPairs(Connection connection) throws SQLException {
        List<TransferPair> pairs = new ArrayList<>(PAIR_COUNT);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(PASSWORD);
        String runId = Long.toString(System.currentTimeMillis());

        for (int index = 0; index < PAIR_COUNT; index++) {
            int sequence = index + 1;
            UserRow sender = insertUser(connection, encodedPassword, runId, sequence, true);
            UserRow receiver = insertUser(connection, encodedPassword, runId, sequence, false);
            Long senderWalletId = insertWallet(connection, sender.userId());
            Long receiverWalletId = insertWallet(connection, receiver.userId());
            insertBalance(connection, senderWalletId, CURRENCY, SENDER_BALANCE);
            insertBalance(connection, receiverWalletId, CURRENCY, RECEIVER_BALANCE);

            pairs.add(new TransferPair(
                    sender.userId(),
                    receiver.userId(),
                    senderWalletId,
                    receiverWalletId,
                    receiver.name(),
                    receiver.phoneNumber(),
                    generateAccessToken(sender.userId())
            ));
        }

        return pairs;
    }

    private static UserRow insertUser(Connection connection, String encodedPassword, String runId,
                                      int sequence, boolean sender) throws SQLException {
        String role = sender ? "sender" : "receiver";
        String email = "transfer-k6-" + role + "-" + runId + "-" + sequence + "@example.com";
        String name = (sender ? "s" : "r") + Integer.toString(sequence, 36);
        String nickname = "t" + role.charAt(0) + runId.substring(Math.max(0, runId.length() - 6)) + sequence;
        String phoneNumber = phoneNumber(sequence, runId, sender);

        String sql = """
                insert into users (
                    user_email,
                    password,
                    user_name,
                    user_nickname,
                    user_phonenumber,
                    user_age,
                    user_sex,
                    user_type,
                    is_deleted,
                    user_join_date
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, encodedPassword);
            ps.setString(3, name);
            ps.setString(4, nickname);
            ps.setString(5, phoneNumber);
            ps.setInt(6, 0);
            ps.setString(7, sender ? "MALE" : "FEMALE");
            ps.setString(8, "ROLE_USER");
            ps.setBoolean(9, false);
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new UserRow(rs.getLong("user_id"), name, phoneNumber);
                }
            }
        }

        throw new IllegalStateException("Failed to create transfer benchmark user.");
    }

    private static Long insertWallet(Connection connection, Long userId) throws SQLException {
        String sql = """
                insert into wallet (
                    wallet_password,
                    user_id,
                    wallet_created_at
                )
                values (?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "000000");
            ps.setLong(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong("wallet_id");
                }
            }
        }

        throw new IllegalStateException("Failed to create transfer benchmark wallet.");
    }

    private static void insertBalance(Connection connection, Long walletId, String currency, BigDecimal balance)
            throws SQLException {
        String sql = """
                insert into balance (
                    wallet_id,
                    currency,
                    balance,
                    currency_created_at,
                    currency_modified_at
                )
                values (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
            ps.setLong(1, walletId);
            ps.setString(2, currency);
            ps.setBigDecimal(3, balance);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
    }

    private static String generateAccessToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 30))
                .signWith(key)
                .compact();
    }

    private static void writeEnv(List<TransferPair> pairs) throws IOException {
        List<String> lines = List.of(
                "AUTH_TOKEN=" + pairs.get(0).accessToken(),
                "ACCESS_TOKEN_COOKIE=" + pairs.get(0).accessToken(),
                "AUTH_TOKENS=" + join(pairs.stream().map(TransferPair::accessToken).toList()),
                "SENDER_USER_ID=" + pairs.get(0).senderUserId(),
                "RECEIVER_USER_ID=" + pairs.get(0).receiverUserId(),
                "SENDER_WALLET_ID=" + pairs.get(0).senderWalletId(),
                "RECEIVER_WALLET_ID=" + pairs.get(0).receiverWalletId(),
                "SENDER_USER_IDS=" + join(pairs.stream().map(TransferPair::senderUserId).toList()),
                "RECEIVER_USER_IDS=" + join(pairs.stream().map(TransferPair::receiverUserId).toList()),
                "SENDER_WALLET_IDS=" + join(pairs.stream().map(TransferPair::senderWalletId).toList()),
                "RECEIVER_WALLET_IDS=" + join(pairs.stream().map(TransferPair::receiverWalletId).toList()),
                "RECEIVER_NAMES=" + join(pairs.stream().map(TransferPair::receiverName).toList()),
                "RECEIVER_PHONE_NUMBERS=" + join(pairs.stream().map(TransferPair::receiverPhoneNumber).toList()),
                "CURRENCY=" + CURRENCY,
                "FROM_CURRENCY=" + CURRENCY,
                "TO_CURRENCY=" + CURRENCY,
                "TRANSFER_AMOUNT=" + TRANSFER_AMOUNT
        );
        Files.write(OUTPUT_DIR.resolve("transaction-transfer-auth.env"), lines, StandardCharsets.UTF_8);
    }

    private static void writeReadme(List<TransferPair> pairs) throws IOException {
        List<String> lines = List.of(
                "# 송금 k6 seed 결과",
                "",
                "- pair 수: " + pairs.size(),
                "- 통화: " + CURRENCY,
                "- 송금액: " + TRANSFER_AMOUNT,
                "- sender user ids: " + join(pairs.stream().map(TransferPair::senderUserId).toList()),
                "- receiver user ids: " + join(pairs.stream().map(TransferPair::receiverUserId).toList()),
                "- sender wallet ids: " + join(pairs.stream().map(TransferPair::senderWalletId).toList()),
                "- receiver wallet ids: " + join(pairs.stream().map(TransferPair::receiverWalletId).toList()),
                "- auth env: build/perf/k6/transaction-transfer-auth.env",
                "",
                "이 파일과 auth env는 로컬 benchmark 실행 결과이며 커밋하지 않는다."
        );
        Files.write(OUTPUT_DIR.resolve("transaction-transfer-seed-readme.md"), lines, StandardCharsets.UTF_8);
    }

    private static String phoneNumber(int sequence, String runId, boolean sender) {
        long runNumber = Long.parseLong(runId);
        int groupBase = sender ? 1000 : 5000;
        int group = groupBase + (int) (Math.abs(runNumber / 10) % 4000);
        int last = sequence % 10_000;
        return "010-" + group + "-" + String.format("%04d", last);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private static int intEnv(String name, int defaultValue) {
        return Integer.parseInt(env(name, Integer.toString(defaultValue)));
    }

    private static BigDecimal decimalEnv(String name, String defaultValue) {
        return new BigDecimal(env(name, defaultValue));
    }

    private static String join(List<?> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private record UserRow(Long userId, String name, String phoneNumber) {
    }

    private record TransferPair(
            Long senderUserId,
            Long receiverUserId,
            Long senderWalletId,
            Long receiverWalletId,
            String receiverName,
            String receiverPhoneNumber,
            String accessToken
    ) {
    }
}
