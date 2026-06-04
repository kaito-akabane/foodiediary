package foodiediary.record.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Record {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    
    @Column(columnDefinition = "text")
    private String description;
    
    @Column(name = "coordinate_x", precision = 10, scale = 6)
    private BigDecimal coordinateX;
    
    @Column(name = "coordinate_y", precision = 10, scale = 6)
    private BigDecimal coordinateY;
    
    private LocalDate date;
    
    private String author; // user.id (외래키)
    
    @Enumerated(EnumType.STRING)
    private RecordVisibility visibility;
    
    @Column(name = "`like`")
    private int like;
}