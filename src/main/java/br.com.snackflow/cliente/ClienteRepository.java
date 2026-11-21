package br.com.snackflow.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Integer id);

    /**
     * Busca por trecho do nome ou do CPF. "busca" vazia lista todos;
     * "digitos" é a busca só com números (vazia quando não há dígitos).
     */
    @Query("""
            select c from Cliente c
            where (:somenteAtivos = false or c.ativo = true)
              and (:busca = ''
                   or lower(c.nome) like lower(concat('%', :busca, '%'))
                   or (:digitos <> '' and c.cpf like concat('%', :digitos, '%')))
            order by c.nome
            """)
    List<Cliente> pesquisar(@Param("busca") String busca,
                            @Param("digitos") String digitos,
                            @Param("somenteAtivos") boolean somenteAtivos);
}
