package com.go.ski.team.core.model;

import com.go.ski.team.support.dto.TeamCreateRequestDTO;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "one_to_n_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
public class OneToNOption {

    @Id
    private int teamId;

    @MapsId
    @OneToOne
    @JoinColumn(name="team_id")
    private Team team;

    @Column(nullable = false)
    private Integer oneTwoFee;

    @Column(nullable = false)
    private Integer oneThreeFee;

    @Column(nullable = false)
    private Integer oneFourFee;

    @Column(nullable = false)
    private Integer oneNFee;

    public static OneToNOption createOneToNOption(Team team, TeamCreateRequestDTO requestDTO) {
        OneToNOption oneToNOption = new OneToNOption();
        oneToNOption.team = team;
        oneToNOption.oneTwoFee = requestDTO.getOneTwoFee();
        oneToNOption.oneThreeFee = requestDTO.getOneThreeFee();
        oneToNOption.oneFourFee = requestDTO.getOneFourFee();
        oneToNOption.oneNFee = requestDTO.getOneNFee();
        return oneToNOption;
    }

}
