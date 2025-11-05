package com.beowulf.core.archiver;

public class Archiver {
    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        System.out.println(new Archiver().getGreeting());
    }
}
