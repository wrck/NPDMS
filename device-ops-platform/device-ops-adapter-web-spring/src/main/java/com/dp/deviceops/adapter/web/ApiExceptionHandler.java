package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.ScriptArtifact;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class}) ResponseEntity<Error> badRequest(Exception e) { return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST")); }
    @ExceptionHandler(ScriptArtifact.DomainConflictException.class) ResponseEntity<Error> conflict(Exception e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error("CONFLICT")); }
    @ExceptionHandler(com.dp.deviceops.adapter.web.masterdata.HttpMasterDataQueryAdapter.MasterDataException.class) ResponseEntity<Error> masterData(com.dp.deviceops.adapter.web.masterdata.HttpMasterDataQueryAdapter.MasterDataException e) { return ResponseEntity.status(e.status).body(new Error("MASTER_DATA_UNAVAILABLE")); }
    record Error(String code) { }
}
