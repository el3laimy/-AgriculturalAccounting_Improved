package accounting.util;

import accounting.model.Contact;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContactDataService {

    private final ImprovedDataManager dataManager;

    public ContactDataService() {
        this.dataManager = ImprovedDataManager.getInstance();
    }

    public Optional<Contact> addContact(Contact contact) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            String sql = "INSERT INTO contacts(name, phone, address, is_supplier, is_customer) VALUES(?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                pstmt.setString(1, contact.getName());
                pstmt.setString(2, contact.getPhone());
                pstmt.setString(3, contact.getAddress());
                pstmt.setBoolean(4, contact.isSupplier());
                pstmt.setBoolean(5, contact.isCustomer());
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) return Optional.empty();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        contact.setContactId(generatedKeys.getInt(1));
                        dataManager.logAuditEntry("contacts", contact.getContactId(), "INSERT", null, contact.getName(), "SYSTEM", conn);
                        return Optional.of(contact);
                    } else {
                        throw new SQLException("Creating contact failed, no ID obtained.");
                    }
                }
            }
        });
    }

    // ---==[[ الدالة المضافة حديثاً ]] ==---
    public boolean updateContact(Contact contact) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            String oldValues = getContactAsJson(conn, contact.getContactId());
            String sql = "UPDATE contacts SET name = ?, phone = ?, address = ?, is_supplier = ?, is_customer = ? WHERE contact_id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, contact.getName());
                pstmt.setString(2, contact.getPhone());
                pstmt.setString(3, contact.getAddress());
                pstmt.setBoolean(4, contact.isSupplier());
                pstmt.setBoolean(5, contact.isCustomer());
                pstmt.setInt(6, contact.getContactId());
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    dataManager.logAuditEntry("contacts", contact.getContactId(), "UPDATE", oldValues, contact.getName(), "SYSTEM", conn);
                }
                return affectedRows > 0;
            }
        });
    }

    // ---==[[ الدالة المضافة حديثاً ]] ==---
    public boolean deleteContact(int contactId) throws SQLException {
        return dataManager.executeTransaction(conn -> {
            if (hasContactTransactions(conn, contactId)) {
                throw new SQLException("لا يمكن حذف جهة التعامل لوجود معاملات مرتبطة بها.");
            }
            
            String oldValues = getContactAsJson(conn, contactId);
            String sql = "UPDATE contacts SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE contact_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, contactId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    dataManager.logAuditEntry("contacts", contactId, "DELETE", oldValues, null, "SYSTEM", conn);
                }
                return rowsAffected > 0;
            }
        });
    }

    public List<Contact> getAllContacts() throws SQLException {
        String sql = "SELECT contact_id, name, phone, address, is_supplier, is_customer FROM contacts WHERE is_active = 1 ORDER BY name";
        List<Contact> contacts = new ArrayList<>();
        try (Connection conn = dataManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                contacts.add(mapResultSetToContact(rs));
            }
        }
        return contacts;
    }
    
    private Contact mapResultSetToContact(ResultSet rs) throws SQLException {
        return new Contact(
            rs.getInt("contact_id"),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getBoolean("is_supplier"),
            rs.getBoolean("is_customer")
        );
    }
    
    private String getContactAsJson(Connection conn, int contactId) throws SQLException {
        String query = "SELECT name FROM contacts WHERE contact_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, contactId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return null;
    }

    private boolean hasContactTransactions(Connection conn, int contactId) throws SQLException {
        String query = """
            SELECT 1 FROM purchases WHERE supplier_id = ?
            UNION
            SELECT 1 FROM sales WHERE customer_id = ?
            LIMIT 1
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, contactId);
            stmt.setInt(2, contactId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}