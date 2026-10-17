
package civiceye.model;

public class Citizen extends User {

    private int rewardPoints;

    public Citizen(int userId, String name, String email) {
        super(userId, name, email);
        this.rewardPoints = 0;
    }

    public void addRewardPoints(int points) {
        if (points > 0) {
            rewardPoints += points;
        }
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    @Override
    public String getRole() {
        return "Citizen";
    }
}

