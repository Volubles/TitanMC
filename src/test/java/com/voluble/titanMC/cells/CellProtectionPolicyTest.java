package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.region.CellProtectionPolicy;
import com.voluble.titanMC.regions.model.*;
import com.voluble.titanMC.regions.protection.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CellProtectionPolicyTest {
	@Test void membersCanBuildWhileVisitorsCanOnlyEnter(){
		UUID member=UUID.randomUUID(); WorldId world=new WorldId(UUID.randomUUID());
		RegionDefinition region=new RegionDefinition(RegionId.random(),RegionKey.of("cell","a1"),world,200,new CuboidGeometry(BlockBox.inclusive(0,0,0,5,5,5)),RegionAccessSet.of(Set.of(member),Set.of()),RegionFlagSet.empty(),RegionTextSet.empty(),Instant.EPOCH,Instant.EPOCH,1);
		CellProtectionPolicy policy=new CellProtectionPolicy(); ProtectionActor tenant=ProtectionActor.player(member,Set.of()); ProtectionActor visitor=ProtectionActor.player(UUID.randomUUID(),Set.of()); BlockPosition at=new BlockPosition(world,1,1,1);
		assertEquals(ProtectionDecision.ALLOW,policy.decide(ProtectionRequest.at(tenant,ProtectionAction.BLOCK_PLACE,at),region));
		assertEquals(ProtectionDecision.DENY,policy.decide(ProtectionRequest.at(visitor,ProtectionAction.BLOCK_PLACE,at),region));
		assertEquals(ProtectionDecision.ALLOW,policy.decide(ProtectionRequest.at(visitor,ProtectionAction.ENTRY,at),region));
	}
}
