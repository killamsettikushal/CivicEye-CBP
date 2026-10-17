
package civiceye.model;

public class Admin extends User {

    public Admin(int userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public String getRole() {
        return "Admin";
    }

    public void displayAdminMessage() {
        System.out.println("Admin can manage civic issues.");
    }
}
