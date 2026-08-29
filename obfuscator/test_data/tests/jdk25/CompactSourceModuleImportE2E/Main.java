import module java.base;

void main() {
    List<String> values = List.of("compact", "source", "main");
    String joined = values.stream()
            .map(String::toUpperCase)
            .collect(Collectors.joining("|"));

    System.out.println(joined);
    System.out.println(LocalDate.of(2025, 9, 16).plusDays(values.size()));
}
