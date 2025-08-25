import java.util.ArrayList;
import java.util.Scanner;

class Student {
    String name;
    double grade;

    Student(String name, double grade) {
        this.name = name;
        this.grade = grade;
    }
}

public class StudentGradeTracker {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        ArrayList<Student> students = new ArrayList<>();
        int choice;

        do {
            System.out.println("\n--- Student Grade Tracker ---");
            System.out.println("1. Add Student");
            System.out.println("2. Display All Students");
            System.out.println("3. Show Summary Report");
            System.out.println("4. Exit");
            System.out.print("Enter choice: ");
            choice = sc.nextInt();
            sc.nextLine(); // consume newline

            switch (choice) {
                case 1:
                    System.out.print("Enter student name: ");
                    String name = sc.nextLine();
                    System.out.print("Enter grade: ");
                    double grade = sc.nextDouble();
                    students.add(new Student(name, grade));
                    System.out.println("Student added successfully!");
                    break;

                case 2:
                    if (students.isEmpty()) {
                        System.out.println("No students found.");
                    } else {
                        System.out.println("\n--- All Students ---");
                        for (Student s : students) {
                            System.out.println(s.name + " - " + s.grade);
                        }
                    }
                    break;

                case 3:
                    if (students.isEmpty()) {
                        System.out.println("No data to summarize.");
                    } else {
                        double total = 0;
                        double highest = students.get(0).grade;
                        double lowest = students.get(0).grade;
                        String topStudent = students.get(0).name;
                        String lowStudent = students.get(0).name;

                        for (Student s : students) {
                            total += s.grade;
                            if (s.grade > highest) {
                                highest = s.grade;
                                topStudent = s.name;
                            }
                            if (s.grade < lowest) {
                                lowest = s.grade;
                                lowStudent = s.name;
                            }
                        }

                        double average = total / students.size();
                        System.out.println("\n--- Summary Report ---");
                        System.out.printf("Average Score: %.2f\n", average);
                        System.out.println("Highest Score: " + highest + " (" + topStudent + ")");
                        System.out.println("Lowest Score: " + lowest + " (" + lowStudent + ")");
                    }
                    break;

                case 4:
                    System.out.println("Exiting program...");
                    break;

                default:
                    System.out.println("Invalid choice. Try again.");
            }
        } while (choice != 4);

        sc.close();
    }
}
