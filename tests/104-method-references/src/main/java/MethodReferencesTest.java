/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */ 
 
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.Set;
import java.util.HashSet;
import java.time.chrono.IsoChronology;

public class MethodReferencesTest {
    
    // The method transferElements copies elements from one collection to
    // another
  
    public static <T, SOURCE extends Collection<T>, DEST extends Collection<T>>
        DEST transferElements(
            SOURCE sourceCollection,
            Supplier<DEST> collectionFactory) {
        
            DEST result = collectionFactory.get();
            for (T t : sourceCollection) {
                result.add(t);
            }
            return result;
    }  
      
    public static void main(String... args) {

        List<Person> roster = Person.createRoster();        

        for (Person p : roster) {
            p.printPerson();
        }
     
        
        Person[] rosterAsArray = 
            roster.toArray(new Person[roster.size()]);
        
        class PersonAgeComparator
            implements Comparator<Person> {
            public int compare(Person a, Person b) {
                LocalDate aDate = a.getBirthday();
                LocalDate bDate = b.getBirthday();
                if (aDate == bDate)
                    return 0;
                else if (aDate == null && bDate != null)
                    return 1;
                else if (aDate != null && bDate == null)
                    return -1;
                else
                    return aDate.compareTo(bDate);
                // Original code, breaks Java 8 TimSort.
//                return a.getBirthday().compareTo(b.getBirthday());
            }
        }
        
        // Without method reference
        Arrays.sort(rosterAsArray, new PersonAgeComparator());
        
        // With lambda expression
        Arrays.sort(rosterAsArray,
            (Person a, Person b) -> {
                LocalDate aDate = a.getBirthday();
                LocalDate bDate = b.getBirthday();
                if (aDate == bDate)
                    return 0;
                else if (aDate == null && bDate != null)
                    return 1;
                else if (aDate != null && bDate == null)
                    return -1;
                else
                    return aDate.compareTo(bDate);
                // Original code, breaks Java 8 TimSort.
//                return a.getBirthday().compareTo(b.getBirthday());
            }
        );
        
        // With method reference
        Arrays.sort(rosterAsArray, Person::compareByAge);
        
        // Reference to an instance method of a particular object
        class ComparisonProvider {
            public int compareByName(Person a,
                Person b) {
                return a.getName().compareTo(b.getName());
            }
        
            public int compareByAge(Person a,
                Person b) {
                return a.getBirthday().compareTo(b.getBirthday());
            }
        }
        ComparisonProvider myComparisonProvider = new ComparisonProvider();
        Arrays.sort(rosterAsArray, myComparisonProvider::compareByName);
        
        // Reference to an instance method
        // of an arbitrary object of a particular type
        
        String[] stringArray = { "Barbara", "James", "Mary", "John",
            "Patricia", "Robert", "Michael", "Linda" };
        stringArray[0] = new String((new Object()).toString());
        stringArray[1] = new String((new Object()).toString());
        Arrays.sort(stringArray, String::compareToIgnoreCase);

        Set<Person> rosterSetLambda =
            transferElements(roster, () -> { return new HashSet<>(); });
        
        Set<Person> rosterSet = transferElements(
            roster, HashSet::new);
        System.out.println("Printing rosterSet:");
        rosterSet.stream().forEach(p -> p.printPerson());
    }
}
