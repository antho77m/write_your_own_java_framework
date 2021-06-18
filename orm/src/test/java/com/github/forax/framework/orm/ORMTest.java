package com.github.forax.framework.orm;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static com.github.forax.framework.orm.ORM.createRepository;
import static com.github.forax.framework.orm.ORM.createTable;
import static com.github.forax.framework.orm.ORM.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unused")
public class ORMTest {
  @Test
  public void testCurrentConnection() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      var connection = ORM.currentConnection();
      assertNotNull(connection);
    });
  }

  @Test
  public void testCreateTable() throws SQLException {
    record Column(String name, String typeName, int size, boolean isNullable, boolean isAutoIncrement) {}
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(User.class);
      var set = new HashSet<Column>();
      var connection = ORM.currentConnection();
      try {
        var metaData = connection.getMetaData();
        try(var resultSet = metaData.getColumns(null, null, "USER", null)) {
          while(resultSet.next()) {
            var column = new Column(
                resultSet.getString(4),                 // COLUMN_NAME
                resultSet.getString(6),                 // TYPE_NAME
                resultSet.getInt(7),                    // COLUMN_SIZE
                resultSet.getString(18).equals("YES"),  // IS_NULLABLE
                resultSet.getString(23).equals("YES")   // IS_AUTOINCREMENT
            );
            set.add(column);
          }
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }
      assertEquals(Set.of(
          new Column("ID", "BIGINT", 19, false, true),
          new Column("AGE", "INTEGER", 10, false, false),
          new Column("NAME", "VARCHAR", 255, true, false)
      ), set);
    });
  }

  static final class Person {
    private Long id;
    private String name;

    public Person(Long id, String name) {
      this.id = id;
      this.name = name;
    }
    public Person() {
    }

    @Id
    public Long getId() {
      return id;
    }
    public void setId(Long id) {
      this.id = id;
    }
    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Person person && Objects.equals(id, person.id) && Objects.equals(name, person.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, name);
    }

    @Override
    public String toString() {
      return "Person{" +
             "id=" + id +
             ", name='" + name + '\'' +
             '}';
    }
  }

  @Test
  public void testUserDefinedQuery() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      var connection = ORM.currentConnection();
      var query = """
          INSERT INTO PERSON (ID, NAME) VALUES (1, 'Bob');
          INSERT INTO PERSON (ID, NAME) VALUES (2, 'Ana');
          INSERT INTO PERSON (ID, NAME) VALUES (3, 'John');
          INSERT INTO PERSON (ID, NAME) VALUES (4, 'Bob');
          """;
      try {
        try(var statement = connection.createStatement()) {
          statement.executeUpdate(query);
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }

      interface PersonRepository extends Repository<Person, Long> {
        @Query("SELECT * FROM PERSON WHERE name = ?")
        List<Person> findAllUsingAName(String name);
      }
      var repository = ORM.createRepository(PersonRepository.class);
      var list = repository.findAllUsingAName("Bob");
      assertEquals(List.of(1L, 4L), list.stream().map(Person::getId).toList());
    });
  }

  @Test
  public void testFindAll() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      var connection = ORM.currentConnection();
      var query = """
          INSERT INTO PERSON (ID, NAME) VALUES (1, 'iga');
          INSERT INTO PERSON (ID, NAME) VALUES (2, 'biva');
          """;
      try {
        try(var statement = connection.createStatement()) {
          statement.executeUpdate(query);
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }

      interface PersonRepository extends Repository<Person, Long> {}
      var repository = ORM.createRepository(PersonRepository.class);
      var persons = repository.findAll();
      assertEquals(List.of(new Person(1L, "iga"), new Person(2L, "biva")), persons);
    });
  }

  @Test
  public void testFindById() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      var connection = ORM.currentConnection();
      var query = """
          INSERT INTO PERSON (ID, NAME) VALUES (1, 'iga');
          INSERT INTO PERSON (ID, NAME) VALUES (2, 'biva');
          """;
      try {
        try(var statement = connection.createStatement()) {
          statement.executeUpdate(query);
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }

      interface PersonRepository extends Repository<Person, Long> {}
      var repository = ORM.createRepository(PersonRepository.class);
      var person = repository.findById(2L).orElseThrow();
      assertEquals(new Person(2L, "biva"), person);
    });
  }

  @Test
  public void testFindByIdNotFound() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      var connection = ORM.currentConnection();
      var query = """
          INSERT INTO PERSON (ID, NAME) VALUES (1, 'iga');
          INSERT INTO PERSON (ID, NAME) VALUES (2, 'biva');
          """;
      try {
        try(var statement = connection.createStatement()) {
          statement.executeUpdate(query);
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }

      interface PersonRepository extends Repository<Person, Long> {}
      var repository = ORM.createRepository(PersonRepository.class);
      var person = repository.findById(888L);
      assertTrue(person.isEmpty());
    });
  }

  @Test
  public void testSave() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      interface PersonRepository extends Repository<Person, Long> {}
      var repository = ORM.createRepository(PersonRepository.class);

      LongStream.range(0, 5)
          .mapToObj(i -> new Person(i, "person" + i))
          .forEach(repository::save);

      var id = 0L;
      for(var person: repository.findAll()) {
        assertEquals(person.getId(), id);
        assertEquals(person.getName(), "person" + id);
        id++;
      }
    });
  }

  @Test
  public void testFindByName() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Person.class);
      var connection = ORM.currentConnection();
      var query = """
          INSERT INTO PERSON (ID, NAME) VALUES (1, 'iga');
          INSERT INTO PERSON (ID, NAME) VALUES (2, 'biva');
          """;
      try {
        try(var statement = connection.createStatement()) {
          statement.executeUpdate(query);
        }
      } catch(SQLException e) {
        throw new AssertionError(e);
      }

      interface PersonRepository extends Repository<Person, Long> {
        Optional<Person> findByName(String name);
      }
      var repository = ORM.createRepository(PersonRepository.class);
      var person = repository.findByName("biva").orElseThrow();
      assertEquals(new Person(2L, "biva"), person);
    });
  }


  public static class Data {
    private String id;

    @Id
    @GeneratedValue
    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }
  }

  @Test
  public void testSaveReturnValue() throws SQLException {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");
    transaction(dataSource, () -> {
      ORM.createTable(Data.class);
      interface DataRepository extends Repository<Data, String> {}
      var repository = ORM.createRepository(DataRepository.class);

      var data1 = repository.save(new Data());
      assertEquals("1", data1.id);

      var data2 = repository.save(new Data());
      assertEquals("2", data2.id);
    });
  }


  @Table("User")
  interface IUser {
    @Id  // primary key
    @GeneratedValue  // auto_increment
    Long getId();
    void setId(Long id) ;
    String getName();
    void setName(String name);
    int getAge();
    void setAge(int age);
  }

  public final static class User implements IUser {
    private Long id;
    private String name;
    private int age;

    public User() {}  // for reflection

    public User(String name, int age) {
      this(null, name, age);
    }
    public User(Long id, String name, int age) {
      this.id = id;
      this.name = name;
      this.age = age;
    }

    @Id  // primary key
    @GeneratedValue  // auto_increment
    public Long getId() {
      return id;
    }
    public void setId(Long id) {
      this.id = id;
    }
    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }
    public int getAge() {
      return age;
    }
    public void setAge(int age) {
      this.age = age;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof User user && Objects.equals(id, user.id) && Objects.equals(name, user.name) && age == user.age;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, name, age);
    }

    @Override
    public String toString() {
      return "User{" +
             "id=" + id +
             ", name='" + name + '\'' +
             ", age=" + age +
             '}';
    }
  }

  @Test
  public void testQuerySeveralParameters() throws SQLException {
    interface UserRepository extends Repository<User, Long> {
      @Query("SELECT * FROM USER WHERE name = ? AND age >= ?")
      List<User> findAllWithANameGreaterThanAge(String name, int age);
    }

    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");

    var repository = createRepository(UserRepository.class);
    transaction(dataSource, () -> {
      createTable(User.class);

      repository.save(new User("Bob", 24));
      repository.save(new User("Ana", 26));
      repository.save(new User("Bob", 34));
      repository.save(new User("Bob", 63));

      var list = repository.findAllWithANameGreaterThanAge("Bob", 30);
      assertEquals(List.of(
          new User(3L, "Bob", 34),
          new User(4L, "Bob", 63)
      ), list);
    });
  }

  @Test
  public void testCreateRepository() throws SQLException {
    interface UserRepository extends Repository<User, Long> {
      Optional<User> findByName(String name);
    }

    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");

    var repository = createRepository(UserRepository.class);
    transaction(dataSource, () -> {
      createTable(User.class);

      var bob = repository.save(new User("Bob", 34));
      assertEquals(new User(1L, "Bob", 34), bob);

      var ana = repository.save(new User("Ana", 37));
      assertEquals(new User(2L, "Ana", 37), ana);

      var bob2 = repository.findById(bob.getId()).orElseThrow();
      assertEquals(new User(1L, "Bob", 34), bob2);

      bob2.setAge(101);
      repository.save(bob2);
      var bob3 = repository.findByName("Bob").orElseThrow();
      assertEquals(new User(1L, "Bob", 101), bob3);

      ana.setAge(77);
      repository.save(ana);
      var ana2 = repository.findByName("Ana").orElseThrow();
      assertEquals(new User(2L, "Ana", 77), ana2);

      var all = repository.findAll();
      assertEquals(List.of(
          new User(1L, "Bob", 101),
          new User(2L, "Ana", 77)
      ), all);
    });
  }

  @Test
  public void testCreateInterfaceRepository() throws SQLException {
    interface IUserRepository extends Repository<IUser, Long> {
      Optional<IUser> findByName(String name);
    }

    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");

    var repository = createRepository(IUserRepository.class);
    transaction(dataSource, () -> {
      createTable(IUser.class);

      var bob = repository.save(new User("Bob", 34));
      assertEquals(1L, bob.getId());
      assertEquals("Bob", bob.getName());
      assertEquals(34, bob.getAge());

      var ana = repository.save(new User("Ana", 37));
      assertEquals(2L, ana.getId());
      assertEquals("Ana", ana.getName());
      assertEquals(37, ana.getAge());

      var bob2 = repository.findById(bob.getId()).orElseThrow();
      assertEquals(1L, bob2.getId());
      assertEquals("Bob", bob2.getName());
      assertEquals(34, bob2.getAge());

      bob2.setAge(101);
      repository.save(bob2);
      var bob3 = repository.findByName("Bob").orElseThrow();
      assertEquals(1L, bob3.getId());
      assertEquals("Bob", bob3.getName());
      assertEquals(101, bob3.getAge());

      ana.setAge(77);
      repository.save(ana);
      var ana2 = repository.findByName("Ana").orElseThrow();
      assertEquals(2L, ana2.getId());
      assertEquals("Ana", ana2.getName());
      assertEquals(77, ana2.getAge());

      var all = repository.findAll();
      assertEquals(2, all.size());
      assertEquals(1L, all.get(0).getId());
      assertEquals("Bob", all.get(0).getName());
      assertEquals(101, all.get(0).getAge());
      assertEquals(2L, all.get(1).getId());
      assertEquals("Ana", all.get(1).getName());
      assertEquals(77, all.get(1).getAge());
    });
  }

  @Test
  public void testCreateInterfaceRepositoryWithARecord() throws SQLException {
    interface IUserRepository extends Repository<IUser, Long> {
      Optional<IUser> findByName(String name);
    }

    record UserDTO(String name, int age) implements IUser {
      @Override
      public int getAge() {
        return age;
      }
      @Override
      public void setAge(int age) {
        throw new UnsupportedOperationException();
      }
      @Override
      public String getName() {
        return name;
      }
      @Override
      public void setName(String name) {
        throw new UnsupportedOperationException();
      }
      @Override
      public Long getId() {
        return null;
      }
      @Override
      public void setId(Long id) {
        throw new UnsupportedOperationException();
      }
    }

    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test");

    var repository = createRepository(IUserRepository.class);
    transaction(dataSource, () -> {
      createTable(IUser.class);

      var bob = repository.save(new UserDTO("Bob", 34));
      assertEquals(1L, bob.getId());
      assertEquals("Bob", bob.getName());
      assertEquals(34, bob.getAge());

      var ana = repository.save(new UserDTO("Ana", 37));
      assertEquals(2L, ana.getId());
      assertEquals("Ana", ana.getName());
      assertEquals(37, ana.getAge());

      var bob2 = repository.findById(bob.getId()).orElseThrow();
      assertEquals(1L, bob2.getId());
      assertEquals("Bob", bob2.getName());
      assertEquals(34, bob2.getAge());

      bob2.setAge(101);
      repository.save(bob2);
      var bob3 = repository.findByName("Bob").orElseThrow();
      assertEquals(1L, bob3.getId());
      assertEquals("Bob", bob3.getName());
      assertEquals(101, bob3.getAge());

      ana.setAge(77);
      repository.save(ana);
      var ana2 = repository.findByName("Ana").orElseThrow();
      assertEquals(2L, ana2.getId());
      assertEquals("Ana", ana2.getName());
      assertEquals(77, ana2.getAge());

      var all = repository.findAll();
      assertEquals(2, all.size());
      assertEquals(1L, all.get(0).getId());
      assertEquals("Bob", all.get(0).getName());
      assertEquals(101, all.get(0).getAge());
      assertEquals(2L, all.get(1).getId());
      assertEquals("Ana", all.get(1).getName());
      assertEquals(77, all.get(1).getAge());
    });
  }
}