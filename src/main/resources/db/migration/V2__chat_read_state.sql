-- FASE 6: hasta cuándo leyó cada participante, para calcular los mensajes no leídos.
ALTER TABLE conversation ADD COLUMN user_a_read_at timestamp with time zone;
ALTER TABLE conversation ADD COLUMN user_b_read_at timestamp with time zone;
