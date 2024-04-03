package net.vjdv.filecalli.dto;

/**
 * Para informar el resultado de una operación
 *
 * @param success true si la operación fue exitosa
 * @param message mensaje de la operación
 */
public record Result(boolean success, String message) {

    public static Result success(String message) {
        return new Result(true, message);
    }

    public static Result failure(String message) {
        return new Result(false, message);
    }

}
