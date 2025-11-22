import java.util.ArrayList;
import java.util.List;

public class Main{
    public static void main(String[] args) {
        String html = "";
        try {
            html = java.nio.file.Files.readString(java.nio.file.Path.of("index.html")).strip();
        } catch (java.io.IOException e) {
            System.err.println("Failed to read index.html: " + e.getMessage());
        }
        List<Token> tokens = new ArrayList<>();
        tokens = tokenize(html);
        printTokens(tokens);
        List<Variable> variables = analyzeVariables(tokens);

        

    }

    public static List<Variable> analyzeVariables(List<Token> tokens){
        List<Variable> variables = new ArrayList<>();
        for(int i = 0;i<tokens.size();i++){
            if(tokens.get(i).type() == TokenType.OBRACKET){
                if(i+1 < tokens.size() && tokens.get(i+1).type() == TokenType.IDENTIFIER && tokens.get(i+1).value().equals("var")){
                    i+=3; // Move to var body
                    while(i < tokens.size() && tokens.get(i).type() != TokenType.CBRACKET){
                        if(tokens.get(i).type() == TokenType.IDENTIFIER){
                            String varName = tokens.get(i).value();
                            String varType = tokens.get(i-1).value();
                            i+=2; // Move to var value
                            while(i < tokens.size() && tokens.get(i).type() != TokenType.SEMICOLON){
                                if(tokens.get(i).type() == TokenType.STRING){
                                    String varValue = tokens.get(i).value();
                                    VarType type;
                                    switch(varType){
                                        case "string" -> type = VarType.STRING;
                                        case "number" -> type = VarType.NUMBER;
                                        case "boolean" -> type = VarType.BOOLEAN;
                                        default -> throw new IllegalArgumentException("Unknown variable type: " + varType);
                                    }
                                    variables.add(new Variable(varName,varValue,type));
                                    break;
                                }
                                i++;
                            }
                        }
                        i++;
                    }
                }
            }    
        }
        return variables;
    }


   
    static void printTokens(List<Token> tokens){
        for(Token token : tokens){
            System.out.println(token.type() + " : " + token.value());
        }
    }

    public static List<Token> tokenize(String html){
        List<Token> tokens = new ArrayList<>();
        for(int i = 0;i<html.length();i++){
            if(html.charAt(i) == '<'){
                tokens.add(new Token(TokenType.OBRACKET,"<"));
            }else if(html.charAt(i) == '>'){
                tokens.add(new Token(TokenType.CBRACKET,">"));
            }else if(html.charAt(i) == ';'){
                tokens.add(new Token(TokenType.SEMICOLON,";"));
            }
            else if(html.charAt(i) == '{'){
                tokens.add(new Token(TokenType.OBRACE,"{"));
            }else if(html.charAt(i) == '}'){
                tokens.add(new Token(TokenType.CBRACE,"}"));
            }
            else if(html.charAt(i) == '/'){
                tokens.add(new Token(TokenType.SLASH,"/"));
            }else if(html.charAt(i) == '='){
                tokens.add(new Token(TokenType.EQUALS,"="));    
            }else if(html.charAt(i) == '!'){
                tokens.add(new Token(TokenType.EXCLAMATION,"!"));
            }else if(Character.isDigit(html.charAt(i))){
                StringBuilder numBuilder = new StringBuilder();
                while(i<html.length() && Character.isDigit(html.charAt(i))){
                    numBuilder.append(html.charAt(i));
                    i++;
                }
                i--; // adjust for the extra increment in the inner loop
                tokens.add(new Token(TokenType.NUMBER,numBuilder.toString()));
            }
            else if(html.charAt(i) == '"' || html.charAt(i) == '\''){
                char quoteType = html.charAt(i);
                StringBuilder strBuilder = new StringBuilder();
                i++; // skip opening quote
                while(i<html.length() && html.charAt(i) != quoteType){
                    strBuilder.append(html.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.STRING,strBuilder.toString()));
            }else if(Character.isLetter(html.charAt(i))){
                StringBuilder idBuilder = new StringBuilder();
                while(i<html.length() && (Character.isLetterOrDigit(html.charAt(i)) || html.charAt(i) == '-' || html.charAt(i) == '_')){
                    idBuilder.append(html.charAt(i));
                    i++;
                }
                i--; // adjust for the extra increment in the inner loop
                tokens.add(new Token(TokenType.IDENTIFIER,idBuilder.toString()));
            }
        }
        return tokens;

    }
}


record Token(TokenType type, String value){}

record Variable(String name, String value,VarType type){}

enum VarType{
    STRING,
    NUMBER,
    BOOLEAN,
}

enum TokenType{
    OBRACKET,
    CBRACKET,
    OBRACE,
    CBRACE,
    IDENTIFIER,
    SLASH,
    EQUALS,
    EXCLAMATION,
    STRING,
    SEMICOLON,
    NUMBER
}