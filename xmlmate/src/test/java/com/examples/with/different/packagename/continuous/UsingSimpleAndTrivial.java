package com.examples.with.different.packagename.continuous;

public class UsingSimpleAndTrivial {

	public void useSimple(Simple s){}
	
	public void useTrivial(Trivial t){}
	
	public int fewBranches(int x){
		if(x==0){
			return 0;
		}
		
		if(x==1){
			return 1;
		}
		
		if(x==2){
			return 2;
		}
		
		if(x==4){
			return 4;
		}
		
		if(x==5){
			return 5;
		}
		
		return 3;
	}
}
