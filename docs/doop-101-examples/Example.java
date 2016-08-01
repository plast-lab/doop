public class Example
{
	public static void main(String[] args) throws IOException{
		(new Example()).test(args.length);
                BufferedReader buffer = new BufferedReader(
                       new InputStreamReader(System.in)
                );
                String line=buffer.readLine();
                sanitize(line);
                String result = args[0] + line;

                System.out.println(result);
	}

        static void sanitize(Object o) {
            
        }

	void test(int l) {
		Animal a;
		if (l == 0)
			a = new Cat();
		else
			a = new Dog();

		a.play();

		Cat c1 = new Cat();
		Cat c2 = new Cat();		
		c1.setParent(c2);

		morePlay(c1);
	}

	void morePlay(Cat c) {
		Cat p = c.getParent();
		p.play();

		Animal a1 = p.playWith(c);
		Animal a2 = c.playWith(p);
		// otherwise assignment to return variables is ignored
		if (a1 == a2) return;
	}
}


class Animal {

	void play() {}

	Animal playWith(Animal other) {
		other.play();
		return other;
	}
}

class Cat extends Animal {
	Cat parent;

	void setParent(Cat c) {
		this.parent = c;
	}

	Cat getParent() {
		return this.parent;
	}

	void play() {}
}

class Dog extends Animal {

	void play() {}
}
